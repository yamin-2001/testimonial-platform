package com.yamin.testimonial.testimonials.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the public submission endpoint, bound from the
 * {@code app.submission} configuration prefix (PRD 4.5).
 */
@ConfigurationProperties(prefix = "app.submission")
public class SubmissionProperties {

	/**
	 * How many submissions one IP address may make before it is throttled.
	 */
	private int rateLimitCapacity = 5;

	/**
	 * How long it takes for one submission to become available again.
	 */
	private long rateLimitRefillSeconds = 10;

	/**
	 * Whether the rate limiter should believe the {@code X-Forwarded-For}
	 * header.
	 *
	 * <p>Off by default. Without a proxy in front, the header is
	 * client-supplied, so trusting it would let a caller forge its own key and
	 * sidestep the limit entirely. Turn it on only when a proxy that sets the
	 * header is guaranteed to be in front.
	 */
	private boolean trustProxy = false;

	/**
	 * The name of the hidden field that bots fill in.
	 */
	private String honeypotField = "companyWebsite";

	public int getRateLimitCapacity() {
		return rateLimitCapacity;
	}

	public void setRateLimitCapacity(int rateLimitCapacity) {
		this.rateLimitCapacity = rateLimitCapacity;
	}

	public long getRateLimitRefillSeconds() {
		return rateLimitRefillSeconds;
	}

	public void setRateLimitRefillSeconds(long rateLimitRefillSeconds) {
		this.rateLimitRefillSeconds = rateLimitRefillSeconds;
	}

	public boolean isTrustProxy() {
		return trustProxy;
	}

	public void setTrustProxy(boolean trustProxy) {
		this.trustProxy = trustProxy;
	}

	public String getHoneypotField() {
		return honeypotField;
	}

	public void setHoneypotField(String honeypotField) {
		this.honeypotField = honeypotField;
	}
}
