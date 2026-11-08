package com.yamin.testimonial.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

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
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.RateLimiter;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * The public submission endpoints over HTTP (PRD 4.5).
 *
 * <p>The rules are covered by {@code TestimonialServiceTest}. This test is
 * about the contract the browser depends on: the status codes, the error
 * shape, and that no session is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class PublicTestimonialControllerTest extends PostgresTestContainer {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SpaceService spaceService;

	@Autowired
	private SpaceRepository spaceRepository;

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private RateLimiter rateLimiter;

	private AppUser owner;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		appUserRepository.deleteAll();

		// The limiter is keyed by IP address and every request from the test
		// machine shares one, so each test starts with a fresh limiter.
		rateLimiter.reset();

		owner = verifiedUser("owner@example.com");
	}

	@Test
	@DisplayName("the Space behind a slug is readable without a session")
	void theSpaceIsReadableWithoutASession() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", "What our customers say",
				"acme-reviews", null, true, null);

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/public/spaces/" + space.getSlug(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"title\":\"Acme Reviews\"");
		assertThat(response.getBody()).contains("\"subtitle\":\"What our customers say\"");
		assertThat(response.getBody()).contains("\"ratingEnabled\":true");
		assertThat(response.getBody()).contains("\"accepting\":true");
	}

	@Test
	@DisplayName("the public Space does not carry the owner's details")
	void thePublicSpaceCarriesNothingPrivate() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/public/spaces/" + space.getSlug(), String.class);

		assertThat(response.getBody()).doesNotContain("owner@example.com");
		assertThat(response.getBody()).doesNotContain("testimonialCount");
		assertThat(response.getBody()).doesNotContain("readOnly");
	}

	@Test
	@DisplayName("the public Space says which fields it collects")
	void thePublicSpaceSaysWhichFieldsItCollects() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				Map.of("companyName", Map.of("enabled", false, "required", false)));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/public/spaces/" + space.getSlug(), String.class);

		assertThat(response.getBody())
				.contains("\"companyName\":{\"enabled\":false,\"required\":false}");
		assertThat(response.getBody())
				.contains("\"jobTitle\":{\"enabled\":true,\"required\":false}");
	}

	@Test
	@DisplayName("an unknown slug returns 404")
	void anUnknownSlugReturnsNotFound() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/public/spaces/no-such-space", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("a submission returns 201 without a session")
	void aSubmissionReturnsCreated() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = post(space.getSlug(),
				"{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"message\":\"Great.\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).contains("\"received\":true");
		assertThat(testimonialRepository.countBySpaceId(space.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("a submission with missing fields returns 400 with a field error")
	void missingFieldsReturnFieldErrors() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = post(space.getSlug(), "{\"email\":\"ada@example.com\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"errors\"");
		assertThat(response.getBody()).contains("name");
		assertThat(response.getBody()).contains("message");
	}

	@Test
	@DisplayName("a submission to an unknown slug returns 404")
	void aSubmissionToAnUnknownSlugReturnsNotFound() {
		ResponseEntity<String> response = post("no-such-space",
				"{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"message\":\"Great.\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("a submission that filled the honeypot returns 201 and stores nothing")
	void aHoneypotSubmissionStoresNothing() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		ResponseEntity<String> response = post(space.getSlug(),
				"{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"message\":\"Great.\","
						+ "\"companyWebsite\":\"https://spam.example.com\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(testimonialRepository.countBySpaceId(space.getId())).isZero();
	}

	@Test
	@DisplayName("a read-only Space refuses submissions with 409")
	void aReadOnlySpaceRefusesSubmissions() {
		// The Free plan allows three Spaces, so the fourth is written
		// straight to the database: that is the state a user is left in when
		// they downgrade, and the state the read-only rule exists for.
		for (int i = 1; i <= 3; i++) {
			spaceService.create(owner.getId(), "Space " + i, null, null, null, false, null);
		}
		Space fourth = spaceDirectly(owner, "Space 4");

		ResponseEntity<String> response = post(fourth.getSlug(),
				"{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"message\":\"Great.\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("not accepting responses");
	}

	@Test
	@DisplayName("the public endpoints do not require a session")
	void thePublicEndpointsDoNotRequireASession() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/public/spaces/anything",
				String.class);

		// 404 rather than 401: the endpoint was reached, the Space was not
		// found.
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	/**
	 * A Space written straight to the database, past the plan's limit.
	 */
	private Space spaceDirectly(AppUser user, String title) {
		Space space = new Space();
		space.setOwner(user);
		space.setTitle(title);
		space.setSlug(title.toLowerCase().replace(' ', '-'));
		space.setThemeKey("light");
		space.setFormConfig(new java.util.LinkedHashMap<>());
		space.setEmbedConfig(new java.util.LinkedHashMap<>());
		space.setPublicProfileFields(new java.util.ArrayList<>());
		return spaceRepository.save(space);
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
	 * Posts a submission.
	 */
	private ResponseEntity<String> post(String slug, String json) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		return restTemplate.exchange("/api/public/spaces/" + slug + "/testimonials",
				HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
	}
}
