package com.yamin.testimonial.testimonials.web;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.RateLimiter;
import com.yamin.testimonial.testimonials.TestimonialRepository;
import com.yamin.testimonial.testimonials.config.SubmissionProperties;

/**
 * The rate limit on the public submission endpoint (PRD 4.5).
 *
 * <p>Its own test class, with its own application context, because the
 * limiter is keyed by the caller's IP address and every request from the test
 * machine shares one. A separate context means a separate limiter, so this
 * test's traffic does not throttle the other tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "app.submission.rate-limit-capacity=2",
				"app.submission.rate-limit-refill-seconds=600" })
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class SubmissionRateLimitTest extends PostgresTestContainer {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SpaceService spaceService;

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private SubmissionProperties submissionProperties;

	@Autowired
	private RateLimiter rateLimiter;

	private AppUser owner;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		appUserRepository.deleteAll();

		// The limiter is keyed by IP address and every request from the test
		// machine shares one, so each test starts with a fresh limiter.
		rateLimiter.reset();

		owner = verifiedUser("owner@example.com");
	}

	@Test
	@DisplayName("the capacity is taken from the configuration")
	void theCapacityComesFromTheConfiguration() {
		assertThat(submissionProperties.getRateLimitCapacity()).isEqualTo(2);
	}

	@Test
	@DisplayName("a submission over the limit returns 429 with a Retry-After header")
	void aSubmissionOverTheLimitReturnsTooManyRequests() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		String json = "{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"message\":\"Great.\"}";

		for (int i = 0; i < 2; i++) {
			ResponseEntity<String> allowed = post(space.getSlug(), json);
			assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}

		ResponseEntity<String> refused = post(space.getSlug(), json);

		assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(refused.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
		assertThat(refused.getBody()).contains("Too many submissions");
	}

	@Test
	@DisplayName("a throttled submission is not stored")
	void aThrottledSubmissionIsNotStored() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		String json = "{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"message\":\"Great.\"}";

		for (int i = 0; i < 3; i++) {
			post(space.getSlug(), json);
		}

		assertThat(testimonialRepository.countBySpaceId(space.getId())).isEqualTo(2);
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
