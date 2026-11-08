package com.yamin.testimonial.spaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.billing.Plan;
import com.yamin.testimonial.billing.Subscription;
import com.yamin.testimonial.billing.SubscriptionRepository;
import com.yamin.testimonial.billing.SubscriptionStatus;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * The Spaces endpoints over HTTP (PRD 4.2).
 *
 * <p>The service-level behaviour is covered by {@code SpaceServiceTest}. This
 * test is about the contract the frontend depends on: status codes, the error
 * shape, and owner isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class SpaceControllerTest extends PostgresTestContainer {

	private static final Pattern ID_PATTERN = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"");
	private static final Pattern SLUG_PATTERN = Pattern.compile("\"slug\":\"([^\"]+)\"");

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private com.yamin.testimonial.spaces.SpaceRepository spaceRepository;

	@Autowired
	private com.yamin.testimonial.auth.AuthService authService;

	private AppUser owner;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		subscriptionRepository.deleteAll();
		appUserRepository.deleteAll();

		owner = verifiedUser("owner@example.com");
	}

	@Test
	@DisplayName("creating a Space returns 201 and the Space's details")
	void createReturnsCreated() {
		ResponseEntity<String> response = post("/api/spaces",
				"{\"title\":\"Acme Reviews\",\"subtitle\":\"What our customers say\"}",
				ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).contains("\"slug\":\"acme-reviews\"");
		assertThat(response.getBody()).contains("\"publicUrl\":\"/s/acme-reviews\"");
		assertThat(response.getBody()).contains("\"readOnly\":false");
	}

	@Test
	@DisplayName("creating a Space without a title returns 400 with a field error")
	void createWithoutTitleIsRejected() {
		ResponseEntity<String> response = post("/api/spaces", "{\"subtitle\":\"No title\"}",
				ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"errors\"");
		assertThat(response.getBody()).contains("title");
	}

	@Test
	@DisplayName("a duplicate slug returns 400 with an error on the slug field")
	void duplicateSlugIsRejected() {
		post("/api/spaces", "{\"title\":\"Acme Reviews\"}", ownerHeaders());

		ResponseEntity<String> response = post("/api/spaces",
				"{\"title\":\"Other Reviews\",\"slug\":\"acme-reviews\"}", ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("already taken");
	}

	@Test
	@DisplayName("going over the Space limit returns 409 with the limit")
	void exceedingTheSpaceLimitIsRejected() {
		for (int i = 1; i <= 3; i++) {
			post("/api/spaces", "{\"title\":\"Space " + i + "\"}", ownerHeaders());
		}

		ResponseEntity<String> response = post("/api/spaces", "{\"title\":\"Space 4\"}",
				ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("\"limit\":3");
	}

	@Test
	@DisplayName("listing returns the user's own Spaces only")
	void listReturnsOnlyTheUsersSpaces() {
		AppUser other = verifiedUser("other@example.com");
		post("/api/spaces", "{\"title\":\"Mine\"}", ownerHeaders());
		post("/api/spaces", "{\"title\":\"Theirs\"}", headersFor(other));

		ResponseEntity<String> response = restTemplate.exchange("/api/spaces", HttpMethod.GET,
				new HttpEntity<>(ownerHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("Mine");
		assertThat(response.getBody()).doesNotContain("Theirs");
	}

	@Test
	@DisplayName("another user's Space returns 404")
	void anotherUsersSpaceIsNotFound() {
		AppUser other = verifiedUser("other@example.com");
		ResponseEntity<String> created = post("/api/spaces", "{\"title\":\"Mine\"}", ownerHeaders());
		UUID spaceId = idFrom(created.getBody());

		ResponseEntity<String> response = restTemplate.exchange("/api/spaces/" + spaceId,
				HttpMethod.GET, new HttpEntity<>(headersFor(other)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("an unauthenticated request to the Spaces endpoints returns 401")
	void unauthenticatedRequestsAreRejected() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/spaces", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("updating a Space changes what was sent")
	void updateChangesTheSpace() {
		ResponseEntity<String> created = post("/api/spaces", "{\"title\":\"Acme Reviews\"}",
				ownerHeaders());
		UUID spaceId = idFrom(created.getBody());

		ResponseEntity<String> response = restTemplate.exchange("/api/spaces/" + spaceId,
				HttpMethod.PUT,
				new HttpEntity<>("{\"title\":\"Acme Reviews 2026\",\"ratingEnabled\":true}",
						jsonHeaders(ownerHeaders())),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("Acme Reviews 2026");
		assertThat(response.getBody()).contains("\"ratingEnabled\":true");
	}

	@Test
	@DisplayName("deleting a Space returns 204 and the Space is gone")
	void deleteRemovesTheSpace() {
		ResponseEntity<String> created = post("/api/spaces", "{\"title\":\"Acme Reviews\"}",
				ownerHeaders());
		UUID spaceId = idFrom(created.getBody());

		ResponseEntity<String> response = restTemplate.exchange("/api/spaces/" + spaceId,
				HttpMethod.DELETE, new HttpEntity<>(ownerHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<String> after = restTemplate.exchange("/api/spaces/" + spaceId,
				HttpMethod.GET, new HttpEntity<>(ownerHeaders()), String.class);
		assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("the themes endpoint flags the premium themes for a Free user")
	void themesFlagThePremiumOnes() {
		ResponseEntity<String> response = restTemplate.exchange("/api/spaces/themes",
				HttpMethod.GET, new HttpEntity<>(ownerHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"key\":\"light\",\"label\":\"Light\",\"premium\":false");
		assertThat(response.getBody()).contains("\"key\":\"midnight\"");
		assertThat(response.getBody()).contains("\"premium\":true");
	}

	@Test
	@DisplayName("a Premium user sees no premium flags on the themes")
	void themesHaveNoFlagsOnPremium() {
		premium(owner);

		ResponseEntity<String> response = restTemplate.exchange("/api/spaces/themes",
				HttpMethod.GET, new HttpEntity<>(ownerHeaders()), String.class);

		assertThat(response.getBody()).doesNotContain("\"premium\":true");
	}

	@Test
	@DisplayName("the dashboard summary counts only the user's own data")
	void summaryCountsOnlyTheUsersData() {
		post("/api/spaces", "{\"title\":\"Mine\"}", ownerHeaders());
		AppUser other = verifiedUser("other@example.com");
		post("/api/spaces", "{\"title\":\"Theirs\"}", headersFor(other));

		ResponseEntity<String> response = restTemplate.exchange("/api/spaces/dashboard/summary",
				HttpMethod.GET, new HttpEntity<>(ownerHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"totalSpaces\":1");
		assertThat(response.getBody()).contains("\"plan\":\"FREE\"");
		assertThat(response.getBody()).contains("\"spaceLimit\":3");
	}

	/**
	 * Creates a verified account.
	 */
	private AppUser verifiedUser(String email) {
		AppUser user = new AppUser();
		user.setEmail(email);
		user.setName("Test User");
		user.setPasswordHash("a-hash");
		user.setEmailVerified(true);
		return appUserRepository.save(user);
	}

	/**
	 * Puts the user on the Premium plan.
	 */
	private void premium(AppUser user) {
		Subscription subscription = new Subscription();
		subscription.setUser(user);
		subscription.setPlan(Plan.PREMIUM);
		subscription.setStatus(SubscriptionStatus.ACTIVE);
		subscriptionRepository.save(subscription);
	}

	/**
	 * The request headers that carry the owner's session.
	 */
	private HttpHeaders ownerHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add(HttpHeaders.COOKIE, "session=" + accessTokenFor(owner));
		return headers;
	}

	/**
	 * The request headers that carry another user's session.
	 */
	private HttpHeaders headersFor(AppUser user) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add(HttpHeaders.COOKIE, "session=" + accessTokenFor(user));
		return headers;
	}

	/**
	 * Copies the headers and adds the JSON content type.
	 */
	private HttpHeaders jsonHeaders(HttpHeaders headers) {
		HttpHeaders copy = new HttpHeaders();
		copy.putAll(headers);
		copy.setContentType(MediaType.APPLICATION_JSON);
		return copy;
	}

	/**
	 * Issues an access token for a user, the way a sign-in would.
	 */
	private String accessTokenFor(AppUser user) {
		return authService.issueSession(user.getId()).accessToken();
	}

	private ResponseEntity<String> post(String path, String json, HttpHeaders headers) {
		return restTemplate.exchange(path, HttpMethod.POST,
				new HttpEntity<>(json, jsonHeaders(headers)), String.class);
	}

	/**
	 * Reads the id out of a response body.
	 */
	private UUID idFrom(String body) {
		Matcher matcher = ID_PATTERN.matcher(body);
		assertThat(matcher.find()).as("the body carries an id: %s", body).isTrue();
		return UUID.fromString(matcher.group(1));
	}

	/**
	 * Reads the slug out of a response body.
	 */
	private String slugFrom(String body) {
		Matcher matcher = SLUG_PATTERN.matcher(body);
		assertThat(matcher.find()).as("the body carries a slug: %s", body).isTrue();
		return matcher.group(1);
	}
}
