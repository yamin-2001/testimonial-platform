package com.yamin.testimonial.auth;

import com.yamin.testimonial.common.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A registered account.
 *
 * <p>One user owns many Spaces and, through them, many Testimonials. A user
 * sees only their own data (PRD 6.7); every owner-scoped query filters on
 * this id.
 *
 * <p>The table is named {@code app_users} rather than {@code users} because
 * {@code users} is a reserved word in PostgreSQL.
 *
 * <p>{@code email} is stored exactly as the user typed it, for display.
 * Case-insensitive uniqueness is enforced by a unique index on
 * {@code lower(email)} (PRD 4.1).
 */
@Entity
@Table(name = "app_users")
public class AppUser extends AuditableEntity {

	@Column(name = "email", nullable = false)
	private String email;

	/** BCrypt hash. Null for accounts that only ever signed in with Google. */
	@Column(name = "password_hash")
	private String passwordHash;

	@Column(name = "name")
	private String name;

	/** False until an email/password account confirms its address. Google users are verified. */
	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified = false;

	/**
	 * Google's stable user id for this account.
	 *
	 * <p>Null for accounts that have never signed in with Google. Sign-in looks
	 * this up first, then falls back to the email to link a Google login to an
	 * existing password account (OPEN-A).
	 */
	@Column(name = "google_subject")
	private String googleSubject;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public String getGoogleSubject() {
		return googleSubject;
	}

	public void setGoogleSubject(String googleSubject) {
		this.googleSubject = googleSubject;
	}
}
