package com.yamin.testimonial.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings, bound from the {@code app.auth} configuration prefix.
 *
 * <p>Defaults are development-friendly so the application boots without any
 * secrets configured, but {@code jwtSecret} must be overridden in any deployed
 * environment -- see the comment on that field.
 */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

	/**
	 * Secret used to sign session tokens.
	 *
	 * <p>Must be at least 32 bytes so that HS256 has a strong enough key. The
	 * default is a development placeholder; override it with
	 * {@code AUTH_JWT_SECRET} in every deployed environment.
	 */
	private String jwtSecret = "development-only-secret-change-me-in-production";

	/** How long an access token remains valid. */
	private Duration accessTokenTtl = Duration.ofMinutes(15);

	/** How long a refresh token remains valid. */
	private Duration refreshTokenTtl = Duration.ofDays(7);

	/** Name of the cookie carrying the access token. */
	private String accessTokenCookie = "session";

	/** Name of the cookie carrying the refresh token. */
	private String refreshTokenCookie = "refresh";

	/** Whether cookies require HTTPS. Disabled for local HTTP development. */
	private boolean secureCookies = false;

	public String getJwtSecret() {
		return jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public Duration getAccessTokenTtl() {
		return accessTokenTtl;
	}

	public void setAccessTokenTtl(Duration accessTokenTtl) {
		this.accessTokenTtl = accessTokenTtl;
	}

	public Duration getRefreshTokenTtl() {
		return refreshTokenTtl;
	}

	public void setRefreshTokenTtl(Duration refreshTokenTtl) {
		this.refreshTokenTtl = refreshTokenTtl;
	}

	public String getAccessTokenCookie() {
		return accessTokenCookie;
	}

	public void setAccessTokenCookie(String accessTokenCookie) {
		this.accessTokenCookie = accessTokenCookie;
	}

	public String getRefreshTokenCookie() {
		return refreshTokenCookie;
	}

	public void setRefreshTokenCookie(String refreshTokenCookie) {
		this.refreshTokenCookie = refreshTokenCookie;
	}

	public boolean isSecureCookies() {
		return secureCookies;
	}

	public void setSecureCookies(boolean secureCookies) {
		this.secureCookies = secureCookies;
	}
}
