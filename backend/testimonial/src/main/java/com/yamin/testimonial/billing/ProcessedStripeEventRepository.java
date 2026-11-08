package com.yamin.testimonial.billing;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to the log of handled Stripe events.
 *
 * <p>Used to make webhook processing idempotent: a replayed event id is
 * already present, so its effects are not applied twice (PRD 4.9, 6.14).
 */
public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {
}
