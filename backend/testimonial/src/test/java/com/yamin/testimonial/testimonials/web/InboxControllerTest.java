package com.yamin.testimonial.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * The inbox endpoints over HTTP (PRD 4.7).
 *
 * <p>The service-level behaviour is covered by {@code InboxServiceTest}. This
 * test is about the contract the frontend depends on: the status codes, the
 * shape of the page, and owner isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class InboxControllerTest extends PostgresTestContainer {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private AuthService authService;

	@Autowired
	private SpaceService spaceService;

	@Autowired
	private SpaceRepository spaceRepository;

	@Autowired
	private TestimonialRepository testimonialRepository;

	private AppUser owner;

	private Space space;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		appUserRepository.deleteAll();

		owner = verifiedUser("owner@example.com");
		space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false, null);
	}

	@Test
	@DisplayName("the inbox is a 401 without a session")
	void theInboxNeedsASession() {
		ResponseEntity<String> response = restTemplate.getForEntity(inboxPath(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("the inbox returns the Space's testimonials")
	void theInboxListsTheTestimonials() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great product.");

		ResponseEntity<String> response = get(inboxPath(), ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("Ada");
		assertThat(response.getBody()).contains("ada@example.com");
		assertThat(response.getBody()).contains("Great product.");
		assertThat(response.getBody()).contains("\"totalElements\":1");
		assertThat(response.getBody()).contains(ada.getId().toString());
	}

	@Test
	@DisplayName("a new testimonial is visible and not on the Wall of Love")
	void aNewTestimonialIsVisibleAndNotOnTheWallOfLove() {
		testimonial("Ada", "ada@example.com", "Great product.");

		ResponseEntity<String> response = get(inboxPath(), ownerHeaders());

		assertThat(response.getBody()).contains("\"hidden\":false");
		assertThat(response.getBody()).contains("\"wallOfLove\":false");
		assertThat(response.getBody()).contains("\"favorite\":false");
	}

	@Test
	@DisplayName("the search finds a testimonial by name")
	void theSearchFilters() {
		testimonial("Ada Lovelace", "ada@example.com", "Great product.");
		testimonial("Grace Hopper", "grace@example.com", "Also great.");

		ResponseEntity<String> response = get(inboxPath() + "?search=grace", ownerHeaders());

		assertThat(response.getBody()).contains("Grace Hopper");
		assertThat(response.getBody()).doesNotContain("Ada Lovelace");
		assertThat(response.getBody()).contains("\"totalElements\":1");
	}

	@Test
	@DisplayName("the favourite filter keeps only favourites")
	void theFavouriteFilter() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great product.");
		Testimonial grace = testimonial("Grace", "grace@example.com", "Also great.");

		put(inboxPath() + "/" + ada.getId() + "/favorite", "{\"value\":true}", ownerHeaders());

		ResponseEntity<String> response = get(inboxPath() + "?favorite=true", ownerHeaders());

		assertThat(response.getBody()).contains("Ada");
		assertThat(response.getBody()).doesNotContain("Grace Hopper");
	}

	@Test
	@DisplayName("marking a favourite is reflected in the response")
	void markingAFavourite() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great product.");

		ResponseEntity<String> response = put(inboxPath() + "/" + ada.getId() + "/favorite",
				"{\"value\":true}", ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"favorite\":true");
	}

	@Test
	@DisplayName("hiding a testimonial marks it hidden")
	void hidingATestimonial() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great product.");

		ResponseEntity<String> response = put(inboxPath() + "/" + ada.getId() + "/hidden",
				"{\"value\":true}", ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"hidden\":true");
	}

	@Test
	@DisplayName("adding to the Wall of Love is the owner's own action")
	void addingToTheWallOfLove() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great product.");

		ResponseEntity<String> response = put(inboxPath() + "/" + ada.getId() + "/wall-of-love",
				"{\"value\":true}", ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"wallOfLove\":true");
	}

	@Test
	@DisplayName("editing changes the text")
	void editingATestimonial() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great product.");

		ResponseEntity<String> response = restTemplate.exchange(inboxPath() + "/" + ada.getId(),
				HttpMethod.PATCH, new HttpEntity<>("{\"message\":\"The edited text.\"}",
						jsonHeaders(ownerHeaders())), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("The edited text.");
		assertThat(response.getBody()).contains("Ada");
	}

	@Test
	@DisplayName("an edit that empties the text returns 400 with a field error")
	void editingToAnEmptyTextIsRejected() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great product.");

		ResponseEntity<String> response = restTemplate.exchange(inboxPath() + "/" + ada.getId(),
				HttpMethod.PATCH, new HttpEntity<>("{\"message\":\"   \"}", jsonHeaders(ownerHeaders())),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"errors\"");
		assertThat(response.getBody()).contains("message");
	}

	@Test
	@DisplayName("deleting removes the testimonial")
	void deletingATestimonial() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great product.");

		ResponseEntity<String> response = restTemplate.exchange(inboxPath() + "/" + ada.getId(),
				HttpMethod.DELETE, new HttpEntity<>(ownerHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(testimonialRepository.findById(ada.getId())).isEmpty();
	}

	@Test
	@DisplayName("another user's inbox is a 404")
	void anotherUsersInboxIsNotFound() {
		AppUser other = verifiedUser("other@example.com");
		Space otherSpace = spaceService.create(other.getId(), "Other Reviews", null, null, null,
				false, null);

		ResponseEntity<String> response = get("/api/spaces/" + otherSpace.getId() + "/testimonials",
				ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("another user's testimonial is a 404")
	void anotherUsersTestimonialIsNotFound() {
		AppUser other = verifiedUser("other@example.com");
		Space otherSpace = spaceService.create(other.getId(), "Other Reviews", null, null, null,
				false, null);
		Testimonial theirs = testimonialIn(otherSpace, "Theirs", "theirs@example.com", "Not yours.");

		ResponseEntity<String> response = put(inboxPath() + "/" + theirs.getId() + "/favorite",
				"{\"value\":true}", ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("a Space that does not exist is a 404")
	void anUnknownSpaceIsNotFound() {
		ResponseEntity<String> response = get("/api/spaces/" + UUID.randomUUID() + "/testimonials",
				ownerHeaders());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	/**
	 * The path of the owner's inbox.
	 */
	private String inboxPath() {
		return "/api/spaces/" + space.getId() + "/testimonials";
	}

	/**
	 * A testimonial in the owner's Space.
	 */
	private Testimonial testimonial(String name, String email, String message) {
		return testimonialIn(space, name, email, message);
	}

	/**
	 * A testimonial in a given Space.
	 */
	private Testimonial testimonialIn(Space target, String name, String email, String message) {
		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(target);
		testimonial.setName(name);
		testimonial.setEmail(email);
		testimonial.setMessage(message);
		return testimonialRepository.save(testimonial);
	}

	/**
	 * A verified user.
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
	 * Copies the headers and adds the JSON content type.
	 */
	private HttpHeaders jsonHeaders(HttpHeaders headers) {
		HttpHeaders copy = new HttpHeaders();
		copy.putAll(headers);
		copy.setContentType(MediaType.APPLICATION_JSON);
		return copy;
	}

	private ResponseEntity<String> get(String path, HttpHeaders headers) {
		return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> put(String path, String json, HttpHeaders headers) {
		return restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(json, jsonHeaders(headers)),
				String.class);
	}
}
