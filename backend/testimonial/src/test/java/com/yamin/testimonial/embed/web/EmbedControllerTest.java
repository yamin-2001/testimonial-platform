package com.yamin.testimonial.embed.web;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.yamin.testimonial.auth.AuthService;
import com.yamin.testimonial.billing.Plan;
import com.yamin.testimonial.billing.Subscription;
import com.yamin.testimonial.billing.SubscriptionRepository;
import com.yamin.testimonial.billing.SubscriptionStatus;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * The embed endpoints over HTTP (PRD 4.8).
 *
 * <p>The rules are covered by {@code EmbedServiceTest}. This test is about
 * the contract the browser and the embed script depend on: the status codes,
 * the error shape, owner isolation, and that the public endpoint needs no
 * session and says it is cacheable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class EmbedControllerTest extends PostgresTestContainer {

	private static final Pattern ID_PATTERN = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"");
	private static final Pattern SLUG_PATTERN = Pattern.compile("\"slug\":\"([^\"]+)\"");

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private SpaceRepository spaceRepository;

	@Autowired
	private SpaceService spaceService;

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private AuthService authService;

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
	@DisplayName("the embed configuration is readable without a session only by the owner")
	void theConfigurationNeedsASession() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/spaces/" + space.getId() + "/embed", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("the embed configuration is readable by the owner")
	void theOwnerCanReadTheConfiguration() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/spaces/" + space.getId() + "/embed", HttpMethod.GET,
				new HttpEntity<>(ownerHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"layout\":\"masonry\"");
		assertThat(response.getBody()).contains("\"mode\":\"light\"");
	}

	@Test
	@DisplayName("another user's Space returns 404")
	void anotherUsersSpaceIsNotFound() {
		AppUser other = verifiedUser("other@example.com");
		Space space = spaceService.create(other.getId(), "Theirs", null, null, null, false, null);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/spaces/" + space.getId() + "/embed", HttpMethod.GET,
				new HttpEntity<>(ownerHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("saving the configuration works and is read back")
	void savingTheConfigurationWorks() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = put(space.getId(),
				"{\"layout\":\"masonry\",\"mode\":\"dark\",\"publicProfileFields\":[\"companyName\"]}",
				ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"mode\":\"dark\"");
		assertThat(response.getBody()).contains("\"key\":\"companyName\",\"enabled\":true");
	}

	@Test
	@DisplayName("a premium option is rejected for a Free user with 400")
	void aPremiumOptionIsRejectedForAFreeUser() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = put(space.getId(), "{\"layout\":\"carousel\"}",
				ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("Premium");
	}

	@Test
	@DisplayName("the public embed endpoint is readable from another origin")
	void thePublicEndpointAllowsACrossOriginRead() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);
		testimonial(space, "Ada", "Great.", true, false);

		// The embed script runs on somebody else's page, so the browser sends
		// a cross-origin request. Without this header the browser refuses the
		// response and the widget renders nothing (PRD 4.8).
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.ORIGIN, "https://a-customer-site.example");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/public/embed/" + space.getSlug(), HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getAccessControlAllowOrigin())
				.isEqualTo("https://a-customer-site.example");
	}

	@Test
	@DisplayName("the public embed endpoint is readable without a session")
	void thePublicEndpointNeedsNoSession() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);
		testimonial(space, "Ada", "Great.", true, false);

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/public/embed/" + space.getSlug(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"name\":\"Ada\"");
	}

	@Test
	@DisplayName("the public embed endpoint says it is cacheable")
	void thePublicEndpointIsCacheable() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/public/embed/" + space.getSlug(), String.class);

		assertThat(response.getHeaders().getCacheControl()).contains("max-age");
		assertThat(response.getHeaders().getCacheControl()).contains("public");
	}

	@Test
	@DisplayName("the public embed endpoint does not expose the email")
	void thePublicEndpointDoesNotExposeTheEmail() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);
		testimonial(space, "Ada", "Great.", true, false);

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/public/embed/" + space.getSlug(), String.class);

		assertThat(response.getBody()).doesNotContain("ada@example.com");
	}

	@Test
	@DisplayName("the public embed endpoint is read-only")
	void thePublicEndpointIsReadOnly() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/public/embed/" + space.getSlug(), HttpMethod.POST,
				new HttpEntity<>("{}", jsonHeaders(new HttpHeaders())), String.class);

		assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
	}

	@Test
	@DisplayName("an unknown slug returns 404 on the public endpoint")
	void anUnknownSlugReturnsNotFound() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/public/embed/no-such-space", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("a deleted Space's embed returns 404")
	void aDeletedSpacesEmbedReturnsNotFound() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);
		String slug = space.getSlug();

		restTemplate.exchange("/api/spaces/" + space.getId(), HttpMethod.DELETE,
				new HttpEntity<>(ownerHeaders()), String.class);

		ResponseEntity<String> response = restTemplate.getForEntity("/api/public/embed/" + slug,
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
	 * A testimonial in a Space.
	 */
	private Testimonial testimonial(Space space, String name, String message, boolean wallOfLove,
			boolean hidden) {

		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(space);
		testimonial.setName(name);
		testimonial.setEmail(name.toLowerCase() + "@example.com");
		testimonial.setMessage(message);
		testimonial.setWallOfLove(wallOfLove);
		testimonial.setHidden(hidden);
		return testimonialRepository.save(testimonial);
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
	 * Issues an access token for a user, the way a sign-in would.
	 */
	private String accessTokenFor(AppUser user) {
		return authService.issueSession(user.getId()).accessToken();
	}

	/**
	 * Copies the headers and makes sure the content type is JSON.
	 */
	private HttpHeaders jsonHeaders(HttpHeaders headers) {
		HttpHeaders copy = new HttpHeaders();
		copy.putAll(headers);
		copy.setContentType(MediaType.APPLICATION_JSON);
		return copy;
	}

	private ResponseEntity<String> put(UUID spaceId, String json, HttpHeaders headers) {
		return restTemplate.exchange("/api/spaces/" + spaceId + "/embed", HttpMethod.PUT,
				new HttpEntity<>(json, jsonHeaders(headers)), String.class);
	}
}
