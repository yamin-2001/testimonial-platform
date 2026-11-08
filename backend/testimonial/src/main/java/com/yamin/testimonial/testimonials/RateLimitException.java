package com.yamin.testimonial.testimonials;

/**
 * Raised when a submitter has made too many submissions in a short time
 * (PRD 4.5).
 *
 * <p>Carries how long the submitter should wait, so the response can say so
 * in a {@code Retry-After} header.
 */
public class RateLimitException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/** How long the submitter should wait before trying again. */
	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
