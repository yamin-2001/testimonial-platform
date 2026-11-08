package com.yamin.testimonial.auth.token;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to the refresh tokens that have been issued and are still live.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

	/**
	 * The live refresh tokens belonging to a user.
	 *
	 * <p>Used to end every session a user holds, which is what a password
	 * reset has to do.
	 */
	List<RefreshToken> findAllByUserId(UUID userId);

	/**
	 * Removes the tokens that would have expired anyway.
	 *
	 * <p>Keeps the table from growing without bound.
	 */
	@Modifying
	@Query("delete from RefreshToken t where t.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}
