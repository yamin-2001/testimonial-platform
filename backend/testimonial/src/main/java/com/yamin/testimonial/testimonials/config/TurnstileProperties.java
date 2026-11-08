package com.yamin.testimonial.testimonials.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare Turnstile settings, bound from the {@code app.turnstile}
 * configuration prefix (PRD 4.5).
 */
@ConfigurationProperties(prefix = "app.turnstile")
public class TurnstileProperties {

	/**
	 * The secret key used to verify a CAPTCHA token.
	 *
	 * <p>Blank means the CAPTCHA is not configured, and verification is
	 * skipped entirely. That keeps the development and test flows working
	 * without a Cloudflare account.
	 */
	private String secretKey = "";

	/**
	 * Where the token is sent for verification.
	 */
	private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getVerifyUrl() {
		return verifyUrl;
	}

	public void setVerifyUrl(String verifyUrl) {
		this.verifyUrl = verifyUrl;
	}

	/**
	 * Whether a CAPTCHA secret is configured.
	 */
	public boolean isConfigured() {
		return secretKey != null && !secretKey.isBlank();
	}
}
