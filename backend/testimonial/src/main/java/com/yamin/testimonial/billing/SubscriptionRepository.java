package com.yamin.testimonial.billing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to subscription state.
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

	/** The single subscription row for a user. */
	Optional<Subscription> findByUserId(UUID userId);

	Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

	Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
