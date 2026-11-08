package com.yamin.testimonial.auth;

import java.time.Instant;

import com.yamin.testimonial.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single-use, expiring link for resetting a password (PRD 4.1).
 *
 * <p>Only the hash of the token is stored. Kept separate from
 * {@link EmailVerificationToken} so the two link types can never be confused.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	/** Set when the token is redeemed. Null while unused. */
	@Column(name = "used_at")
	private Instant usedAt;

	public AppUser getUser() {
		return user;
	}

	public void setUser(AppUser user) {
		this.user = user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public void setUsedAt(Instant usedAt) {
		this.usedAt = usedAt;
	}

	/**
	 * Whether this token may still be redeemed.
	 *
	 * <p>Redemption must use a conditional update rather than reading this
	 * flag first, otherwise two concurrent requests could both redeem the same
	 * token.
	 */
	public boolean isRedeemable() {
		return usedAt == null && expiresAt.isAfter(Instant.now());
	}
}
