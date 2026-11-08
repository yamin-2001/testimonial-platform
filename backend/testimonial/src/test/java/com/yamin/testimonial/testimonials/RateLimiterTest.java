package com.yamin.testimonial.testimonials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yamin.testimonial.testimonials.config.SubmissionProperties;

/**
 * Rate limiting public submissions per IP address (PRD 4.5).
 */
class RateLimiterTest {

	private SubmissionProperties properties;

	private RateLimiter rateLimiter;

	@BeforeEach
	void setUp() {
		properties = new SubmissionProperties();
		properties.setRateLimitCapacity(3);
		properties.setRateLimitRefillSeconds(60);

		rateLimiter = new RateLimiter(properties);
	}

	@Test
	@DisplayName("an address may submit up to its capacity")
	void anAddressMaySubmitUpToItsCapacity() {
		for (int i = 0; i < 3; i++) {
			rateLimiter.consume("203.0.113.9");
		}
	}

	@Test
	@DisplayName("an address over its capacity is refused")
	void anAddressOverItsCapacityIsRefused() {
		for (int i = 0; i < 3; i++) {
			rateLimiter.consume("203.0.113.9");
		}

		assertThatThrownBy(() -> rateLimiter.consume("203.0.113.9"))
				.isInstanceOf(RateLimitException.class)
				.satisfies(e -> assertThat(((RateLimitException) e).getRetryAfterSeconds())
						.isGreaterThan(0));
	}

	@Test
	@DisplayName("one address over its capacity does not affect another")
	void oneAddressDoesNotAffectAnother() {
		for (int i = 0; i < 3; i++) {
			rateLimiter.consume("203.0.113.9");
		}

		assertThatThrownBy(() -> rateLimiter.consume("203.0.113.9"))
				.isInstanceOf(RateLimitException.class);

		// A different address still has its own full bucket.
		rateLimiter.consume("203.0.113.10");
	}
}
