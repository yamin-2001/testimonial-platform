package com.yamin.testimonial.testimonials;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yamin.testimonial.testimonials.config.SubmissionProperties;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Rate limits public submissions per IP address (PRD 4.5).
 *
 * <p>A token bucket: each IP address starts with a full bucket of tokens and
 * regains one every so often. A submission takes one token. When the bucket is
 * empty the submission is refused and the caller is told how long to wait.
 *
 * <p>The buckets live in a Caffeine cache rather than in a map, so that an
 * attacker cycling through addresses cannot make the application hold an
 * unbounded number of them. An idle address's bucket is dropped after an hour.
 */
@Service
public class RateLimiter {

	private final SubmissionProperties properties;

	/** One bucket per IP address. */
	private final Cache<String, Bucket> buckets;

	public RateLimiter(SubmissionProperties properties) {
		this.properties = properties;
		this.buckets = Caffeine.newBuilder()
				// An address that stops submitting for an hour is forgotten.
				.expireAfterAccess(Duration.ofHours(1))
				.maximumSize(100_000)
				.build();
	}

	/**
	 * Takes one token for the given address.
	 *
	 * @throws RateLimitException if the address has no tokens left
	 */
	public void consume(String address) {
		ConsumptionProbe probe = bucketFor(address).tryConsumeAndReturnRemaining(1);

		if (!probe.isConsumed()) {
			throw new RateLimitException(
					"Too many submissions. Please try again shortly.",
					secondsUntilNextToken(probe));
		}
	}

	/**
	 * Forgets every address's bucket.
	 *
	 * <p>Only for tests. The buckets are keyed by IP address, and every
	 * request from a test machine shares one, so a test that wants a fresh
	 * limit needs a fresh limiter.
	 */
	public void reset() {
		buckets.invalidateAll();
	}

	/**
	 * The bucket for an address, built on first use.
	 *
	 * <p>The configuration is read when the bucket is built rather than once at
	 * startup, so a change to the capacity or the refill rate takes effect for
	 * new addresses without a restart.
	 */
	private Bucket bucketFor(String address) {
		return buckets.get(address, key -> Bucket.builder()
				.addLimit(limit())
				.build());
	}

	/**
	 * The limit itself: a bucket of {@code capacity} tokens that regains one
	 * token every {@code refillSeconds}.
	 */
	private Bandwidth limit() {
		return Bandwidth.builder()
				.capacity(properties.getRateLimitCapacity())
				.refillGreedy(1, Duration.ofSeconds(properties.getRateLimitRefillSeconds()))
				.build();
	}

	/**
	 * How long until the address has a token again.
	 *
	 * <p>Rounded up, because telling someone to wait zero seconds is not an
	 * answer.
	 */
	private long secondsUntilNextToken(ConsumptionProbe probe) {
		long nanos = probe.getNanosToWaitForRefill();
		if (nanos <= 0) {
			return 1;
		}
		return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
	}
}
