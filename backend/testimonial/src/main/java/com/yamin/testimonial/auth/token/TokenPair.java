package com.yamin.testimonial.auth.token;

import java.time.Duration;
import java.util.UUID;

/**
 * A freshly issued access/refresh token pair.
 *
 * @param accessToken the signed access token
 * @param accessTokenId the access token's unique id
 * @param accessTokenTtl how long the access token remains valid
 * @param refreshToken the signed refresh token
 * @param refreshTokenId the refresh token's unique id, used to revoke it
 * @param refreshTokenTtl how long the refresh token remains valid
 * @param userId the user the tokens belong to
 */
public record TokenPair(
		String accessToken,
		String accessTokenId,
		Duration accessTokenTtl,
		String refreshToken,
		String refreshTokenId,
		Duration refreshTokenTtl,
		UUID userId) {
}
