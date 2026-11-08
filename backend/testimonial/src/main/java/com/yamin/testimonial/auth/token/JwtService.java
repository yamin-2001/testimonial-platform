package com.yamin.testimonial.auth.token;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.yamin.testimonial.auth.config.AuthProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies the session tokens carried in the auth cookies.
 *
 * <p>Two token kinds are issued:
 * <ul>
 *   <li>the <strong>access token</strong>, which authorises ordinary
 *       requests and is deliberately short-lived;</li>
 *   <li>the <strong>refresh token</strong>, which is only accepted by the
 *       refresh endpoint and is revoked on logout.</li>
 * </ul>
 *
 * <p>Both carry a unique token id ({@code jti}). The refresh token's id is
 * recorded when it is revoked, which is what makes logout take effect
 * immediately rather than when the token would have expired.
 */
@Service
public class JwtService {

	private static final String TOKEN_TYPE_CLAIM = "typ";

	private final AuthProperties properties;

	private final SecretKey signingKey;

	public JwtService(AuthProperties properties) {
		this.properties = properties;
		this.signingKey = Keys.hmacShaKeyFor(secretBytes(properties.getJwtSecret()));
	}

	/**
	 * Issues a new access/refresh token pair for a user.
	 */
	public TokenPair issueTokens(UUID userId) {
		String accessJti = UUID.randomUUID().toString();
		String refreshJti = UUID.randomUUID().toString();
		Instant now = Instant.now();

		String accessToken = buildToken(accessJti, userId, TokenType.ACCESS, now,
				properties.getAccessTokenTtl());
		String refreshToken = buildToken(refreshJti, userId, TokenType.REFRESH, now,
				properties.getRefreshTokenTtl());

		return new TokenPair(accessToken, accessJti, properties.getAccessTokenTtl(),
				refreshToken, refreshJti, properties.getRefreshTokenTtl(), userId);
	}

	/**
	 * Parses and verifies a token.
	 *
	 * @return the claims, or {@code null} if the token is malformed, expired,
	 *         signed with the wrong key, or not of the expected type
	 */
	public Claims parse(String token, TokenType expectedType) {
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			Claims claims = Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			TokenType actualType = TokenType.valueOf(claims.get(TOKEN_TYPE_CLAIM, String.class));
			return actualType == expectedType ? claims : null;
		}
		catch (JwtException | IllegalArgumentException e) {
			// Expired, malformed, tampered with, or wrong key. Either way the
			// token is not usable.
			return null;
		}
	}

	private String buildToken(String jti, UUID userId, TokenType type, Instant now,
			java.time.Duration ttl) {
		return Jwts.builder()
				.id(jti)
				.subject(userId.toString())
				.claim(TOKEN_TYPE_CLAIM, type.name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(ttl)))
				.signWith(signingKey)
				.compact();
	}

	/**
	 * Turns the configured secret into key bytes.
	 *
	 * <p>A short secret is stretched with SHA-256 so that a short passphrase
	 * still yields a 256-bit key. HS256 requires at least 32 bytes, and
	 * failing loudly at startup is better than failing on the first login.
	 */
	private static byte[] secretBytes(String secret) {
		byte[] raw = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		if (raw.length >= 32) {
			return raw;
		}
		try {
			return java.security.MessageDigest.getInstance("SHA-256").digest(raw);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required to derive the JWT signing key", e);
		}
	}

	/**
	 * The kinds of token this service issues.
	 *
	 * <p>The type is checked when a token is parsed, so a refresh token can
	 * never be used as an access token.
	 */
	public enum TokenType {
		ACCESS,
		REFRESH
	}
}
