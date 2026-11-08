package com.yamin.testimonial.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to email verification tokens.
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

	Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

	/**
	 * Redeems a token.
	 *
	 * <p>The update is conditional on the token being unused and unexpired, so
	 * it is atomic: two concurrent requests cannot both redeem the same
	 * link, because only one of them will affect a row.
	 *
	 * @return true if the token was redeemed by this call
	 */
	@Modifying
	@Query("""
			update EmailVerificationToken t
			set t.usedAt = :now
			where t.id = :id and t.usedAt is null and t.expiresAt > :now
			""")
	int consume(@Param("id") UUID id, @Param("now") Instant now);
}
