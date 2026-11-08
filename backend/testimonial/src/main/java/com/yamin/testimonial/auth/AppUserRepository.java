package com.yamin.testimonial.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to registered accounts.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/**
	 * Looks an account up by email, ignoring case.
	 *
	 * <p>Email uniqueness is case-insensitive (PRD 4.1) and is enforced by a
	 * unique index on {@code lower(email)}.
	 */
	Optional<AppUser> findByEmailIgnoreCase(String email);

	/**
	 * Looks an account up by Google's stable user id.
	 *
	 * <p>Used first during Google sign-in. If absent, the sign-in flow falls
	 * back to an email lookup so an existing password account can be linked
	 * (OPEN-A).
	 */
	Optional<AppUser> findByGoogleSubject(String googleSubject);

	boolean existsByEmailIgnoreCase(String email);
}
