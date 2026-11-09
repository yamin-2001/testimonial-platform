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

	/**
	 * Removes the tokens that can no longer be redeemed.
	 *
	 * <p>A token that has passed its expiry is dead whether it was used or
	 * not, and a used token cannot be redeemed again either. Without this the
	 * table grows by one row per password reset requested, forever.
	 *
	 * @return how many rows were removed
	 */
	@Modifying
	@Query("delete from PasswordResetToken t where t.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}
