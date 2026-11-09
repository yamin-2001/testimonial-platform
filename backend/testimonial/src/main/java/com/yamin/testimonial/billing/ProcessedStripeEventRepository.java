package com.yamin.testimonial.billing;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to the log of handled Stripe events.
 *
 * <p>Used to make webhook processing idempotent: a replayed event id is
 * already present, so its effects are not applied twice (PRD 4.9, 6.14).
 */
public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {

	/**
	 * Removes the events that Stripe can no longer retry.
	 *
	 * <p>The row is what makes a replayed event a no-op, so it has to outlive
	 * Stripe's own retry window: Stripe retries an event it thinks was not
	 * acknowledged for up to three days. A row removed before that could let a
	 * late retry apply its change a second time.
	 *
	 * <p>Past that window the row has served its purpose -- a duplicate that
	 * arrives after it is one the application can no longer act on anyway --
	 * and the table would otherwise grow by one row per event, forever.
	 *
	 * @return how many rows were removed
	 */
	@Modifying
	@Query("delete from ProcessedStripeEvent e where e.processedAt < :cutoff")
	int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
