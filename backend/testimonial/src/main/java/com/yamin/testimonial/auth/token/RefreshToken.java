package com.yamin.testimonial.auth.token;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A refresh token that has been issued and is still live.
 *
 * <p>The signed refresh token itself is not stored -- it is stateless, and
 * only its id needs to be remembered. Keeping the record is what allows a
 * user's sessions to be ended in bulk: a password reset revokes every live
 * refresh token belonging to that user, so a token stolen before the reset
 * cannot be used afterwards.
 *
 * <p>A row is removed when the token is refreshed (rotation), when it is
 * revoked, or when it expires.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	/** The refresh token's unique id, taken from the JWT's {@code jti}. */
	@Id
	@Column(name = "jti", nullable = false)
	private String jti;

	/** The user the token belongs to. */
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/** When the token stops being accepted. */
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public RefreshToken() {
	}

	public RefreshToken(String jti, UUID userId, Instant expiresAt, Instant createdAt) {
		this.jti = jti;
		this.userId = userId;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public String getJti() {
		return jti;
	}

	public void setJti(String jti) {
		this.jti = jti;
	}

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
