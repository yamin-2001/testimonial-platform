package com.yamin.testimonial.auth.token;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to revoked refresh tokens.
 */
public interface RevokedRefreshTokenRepository extends JpaRepository<RevokedRefreshToken, String> {

	/**
	 * Removes revocations for tokens that would have expired anyway.
	 *
	 * <p>Keeps the table from growing without bound; a revocation for an
	 * already-expired token can never block anything.
	 */
	@Modifying
	@Query("delete from RevokedRefreshToken r where r.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);

	List<RevokedRefreshToken> findAllByExpiresAtBefore(Instant now);
}
