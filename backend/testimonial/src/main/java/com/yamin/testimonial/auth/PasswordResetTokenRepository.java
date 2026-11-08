package com.yamin.testimonial.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to password reset tokens.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/**
	 * Redeems a token.
	 *
	 * <p>Conditional on the token being unused and unexpired, so the update is
	 * atomic and a reset link can only be used once.
	 *
	 * @return true if the token was redeemed by this call
	 */
	@Modifying
	@Query("""
			update PasswordResetToken t
			set t.usedAt = :now
			where t.id = :id and t.usedAt is null and t.expiresAt > :now
			""")
	int consume(@Param("id") UUID id, @Param("now") Instant now);
}
