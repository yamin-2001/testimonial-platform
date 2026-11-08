package com.yamin.testimonial.analytics.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.auth.AuthService;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * The analytics API over HTTP (PRD 4.6).
 *
 * <p>The rules are covered by {@code AnalyticsServiceTest}. This test is
 * about the contract the dashboard depends on: the shape of the response,
 * that the range can be chosen, and that the endpoint needs a session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AnalyticsControllerTest extends PostgresTestContainer {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SpaceRepository spaceRepository;

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private AuthService authService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private AppUser owner;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		appUserRepository.deleteAll();

		jdbcTemplate.execute("set time zone 'UTC'");

		owner = new AppUser();
		owner.setEmail("owner@example.com");
		owner.setName("Owner");
		owner.setPasswordHash("a-hash");
		owner.setEmailVerified(true);
		owner = appUserRepository.save(owner);
	}

	@Test
	@DisplayName("the analytics need a session")
	void theAnalyticsNeedASession() {
		ResponseEntity<String> response =
				restTemplate.getForEntity("/api/analytics/dashboard", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("the default range is thirty days")
	void theDefaultRangeIsThirtyDays() {
		ResponseEntity<String> response = get("/api/analytics/dashboard");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"range\":30");
		assertThat(countOccurrences(response.getBody(), "\"date\"")).isEqualTo(30);
	}

	@Test
	@DisplayName("the range can be chosen")
	void theRangeCanBeChosen() {
		ResponseEntity<String> response = get("/api/analytics/dashboard?days=7");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"range\":7");
		assertThat(countOccurrences(response.getBody(), "\"date\"")).isEqualTo(7);
	}

	@Test
	@DisplayName("the series includes the empty days as zero")
	void theSeriesIncludesTheEmptyDaysAsZero() {
		Space space = space("Acme Reviews");
		submission(space, "Ada", "ada@example.com");

		ResponseEntity<String> response = get("/api/analytics/dashboard?days=3");

		// Three days, and only one of them has a submission in it.
		assertThat(countOccurrences(response.getBody(), "\"date\"")).isEqualTo(3);
		assertThat(countOccurrences(response.getBody(), "\"count\":0")).isEqualTo(2);
		assertThat(countOccurrences(response.getBody(), "\"count\":1")).isEqualTo(1);
	}

	@Test
	@DisplayName("the recent list names the submitter and the Space")
	void theRecentListNamesTheSubmitterAndTheSpace() {
		Space space = space("Acme Reviews");
		submission(space, "Ada Lovelace", "ada@example.com");

		ResponseEntity<String> response = get("/api/analytics/dashboard");

		assertThat(response.getBody()).contains("\"name\":\"Ada Lovelace\"");
		assertThat(response.getBody()).contains("\"spaceTitle\":\"Acme Reviews\"");
		assertThat(response.getBody()).contains("\"spaceSlug\":\"acme-reviews\"");
	}

	@Test
	@DisplayName("another owner's submissions are not in the response")
	void anotherOwnersSubmissionsAreNotInTheResponse() {
		AppUser other = new AppUser();
		other.setEmail("other@example.com");
		other.setName("Other");
		other.setPasswordHash("a-hash");
		other.setEmailVerified(true);
		other = appUserRepository.save(other);

		submission(space("Acme Reviews"), "Mine", "mine@example.com");
		submission(spaceFor(other, "Other Reviews"), "Not mine", "not-mine@example.com");

		ResponseEntity<String> response = get("/api/analytics/dashboard");

		assertThat(response.getBody()).contains("Mine");
		assertThat(response.getBody()).doesNotContain("Not mine");
		assertThat(response.getBody()).doesNotContain("Other Reviews");
	}

	/**
	 * A GET with the owner's session.
	 */
	private ResponseEntity<String> get(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add(HttpHeaders.COOKIE, "session=" + accessTokenFor(owner));

		return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	/**
	 * How many times a piece of text appears in the body.
	 */
	private int countOccurrences(String body, String needle) {
		int count = 0;
		int at = body.indexOf(needle);

		while (at >= 0) {
			count++;
			at = body.indexOf(needle, at + needle.length());
		}

		return count;
	}

	/**
	 * Creates a Space for the owner.
	 */
	private Space space(String title) {
		return spaceFor(owner, title);
	}

	/**
	 * Creates a Space for a user.
	 */
	private Space spaceFor(AppUser user, String title) {
		Space space = new Space();
		space.setOwner(user);
		space.setTitle(title);
		space.setSlug(title.toLowerCase().replace(' ', '-'));
		space.setThemeKey("light");
		space.setRatingEnabled(false);
		return spaceRepository.save(space);
	}

	/**
	 * Records a testimonial, created now.
	 */
	private Testimonial submission(Space space, String name, String email) {
		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(space);
		testimonial.setName(name);
		testimonial.setEmail(email);
		testimonial.setMessage("A testimonial.");
		testimonial.setConsentGiven(true);
		Testimonial saved = testimonialRepository.saveAndFlush(testimonial);

		jdbcTemplate.update("update testimonials set created_at = ? where id = ?",
				java.sql.Timestamp.from(Instant.now()), saved.getId());

		return saved;
	}

	/**
	 * Issues an access token for a user, the way a sign-in would.
	 */
	private String accessTokenFor(AppUser user) {
		return authService.issueSession(user.getId()).accessToken();
	}
}
