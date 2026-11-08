package com.yamin.testimonial.auth.token;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A refresh token that has been revoked by a logout.
 *
 * <p>Access tokens are stateless and cannot be revoked, so a session is
 * bounded by a short access-token lifetime plus a refresh token. Revoking the
 * refresh token ends the session immediately rather than waiting for it to
 * expire.
 */
@Entity
@Table(name = "revoked_refresh_tokens")
public class RevokedRefreshToken {

	/** The refresh token's unique id. */
	@Id
	@Column(name = "jti", nullable = false)
	private String jti;

	/** When the revoked token would have expired anyway. */
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at", nullable = false)
	private Instant revokedAt;

	public RevokedRefreshToken() {
	}

	public RevokedRefreshToken(String jti, Instant expiresAt, Instant revokedAt) {
		this.jti = jti;
		this.expiresAt = expiresAt;
		this.revokedAt = revokedAt;
	}

	public String getJti() {
		return jti;
	}

	public void setJti(String jti) {
		this.jti = jti;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public void setRevokedAt(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}
}
