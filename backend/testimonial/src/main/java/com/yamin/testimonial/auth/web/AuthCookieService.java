package com.yamin.testimonial.auth.web;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.yamin.testimonial.auth.config.AuthProperties;
import com.yamin.testimonial.auth.token.TokenPair;

/**
 * Writes and clears the session cookies.
 *
 * <p>The cookies are HttpOnly so that page scripts cannot read the tokens,
 * and SameSite=Lax so they are not attached to cross-site requests. The
 * frontend reaches the API through a Next.js rewrite, so the API is
 * same-origin with the app and Lax is sufficient.
 */
@Service
public class AuthCookieService {

	private final AuthProperties properties;

	public AuthCookieService(AuthProperties properties) {
		this.properties = properties;
	}

	/** Builds the cookie carrying the access token. */
	public ResponseCookie accessCookie(String token, Duration ttl) {
		return base(properties.getAccessTokenCookie(), token, ttl);
	}

	/** The name of the cookie carrying the access token. */
	public String accessCookieName() {
		return properties.getAccessTokenCookie();
	}

	/** The name of the cookie carrying the refresh token. */
	public String refreshCookieName() {
		return properties.getRefreshTokenCookie();
	}

	/** Builds the cookie carrying the refresh token. */
	public ResponseCookie refreshCookie(String token, Duration ttl) {
		return base(properties.getRefreshTokenCookie(), token, ttl);
	}

	/** Builds both session cookies for a newly issued token pair. */
	public ResponseCookie[] sessionCookies(TokenPair pair) {
		return new ResponseCookie[] {
				accessCookie(pair.accessToken(), pair.accessTokenTtl()),
				refreshCookie(pair.refreshToken(), pair.refreshTokenTtl())
		};
	}

	/**
	 * Builds a cookie that expires the session cookie immediately.
	 *
	 * <p>The path must match the one used when the cookie was set, or the
	 * browser will keep the original.
	 */
	public ResponseCookie expiredCookie(String name) {
		return ResponseCookie.from(name, "")
				.httpOnly(true)
				.secure(properties.isSecureCookies())
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
	}

	private ResponseCookie base(String name, String value, Duration ttl) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(properties.isSecureCookies())
				.sameSite("Lax")
				.path("/")
				.maxAge(ttl)
				.build();
	}
}
