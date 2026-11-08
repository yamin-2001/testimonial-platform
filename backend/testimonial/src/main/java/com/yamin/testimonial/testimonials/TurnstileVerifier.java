package com.yamin.testimonial.testimonials;

/**
 * Checks a CAPTCHA token (PRD 4.5).
 *
 * <p>Kept as an interface so the submission service does not care how the
 * check is made. The real implementation talks to Cloudflare; a test can
 * substitute its own.
 */
public interface TurnstileVerifier {

	/**
	 * Whether the token is valid.
	 *
	 * <p>Only called when a CAPTCHA secret is configured. When it is not, the
	 * submission service skips the check entirely, so that development and
	 * test environments work without a Cloudflare account.
	 *
	 * @param token the token the browser's widget produced
	 * @param remoteIp the submitter's IP address, which Cloudflare uses to
	 *        strengthen the check; may be null
	 */
	boolean verify(String token, String remoteIp);
}
