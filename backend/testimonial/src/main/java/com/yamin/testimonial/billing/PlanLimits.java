package com.yamin.testimonial.billing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The limits each plan imposes, and the effective plan of a user (PRD 7).
 *
 * <p>Every limit is checked on the backend, never only in the UI (PRD 6.12).
 * This is the single place the limits live, so that a new caller cannot
 * invent a different number.
 *
 * <p>The effective plan comes from {@link Subscription#isPremium()} rather
 * than from {@code plan} directly: a past-due account is still Premium until
 * Stripe gives up, and a cancelled one is not (PRD 4.9).
 */
@Service
public class PlanLimits {

	/** How many Spaces a Free user may own (PRD 7). */
	public static final int FREE_SPACE_LIMIT = 3;

	/** How many Spaces a Premium user may own (PRD 7). */
	public static final int PREMIUM_SPACE_LIMIT = 25;

	/** How many testimonials one Space may hold on Free (PRD 7). */
	public static final int FREE_TESTIMONIAL_LIMIT_PER_SPACE = 100;

	/** How many testimonials one Space may hold on Premium (PRD 7). */
	public static final int PREMIUM_TESTIMONIAL_LIMIT_PER_SPACE = 1_000;

	private final SubscriptionRepository subscriptionRepository;

	public PlanLimits(SubscriptionRepository subscriptionRepository) {
		this.subscriptionRepository = subscriptionRepository;
	}

	/**
	 * Whether a user currently has Premium access.
	 *
	 * <p>A user with no subscription row is on the Free plan.
	 */
	@Transactional(readOnly = true)
	public boolean isPremium(UUID userId) {
		return subscriptionRepository.findByUserId(userId)
				.map(Subscription::isPremium)
				.orElse(false);
	}

	/**
	 * The user's plan.
	 */
	@Transactional(readOnly = true)
	public Plan planOf(UUID userId) {
		return subscriptionRepository.findByUserId(userId)
				.filter(Subscription::isPremium)
				.map(subscription -> Plan.PREMIUM)
				.orElse(Plan.FREE);
	}

	/**
	 * How many Spaces a user on the given plan may own.
	 */
	public int spaceLimitFor(Plan plan) {
		return plan == Plan.PREMIUM ? PREMIUM_SPACE_LIMIT : FREE_SPACE_LIMIT;
	}

	/**
	 * How many testimonials a single Space may hold on the given plan.
	 */
	public int testimonialLimitPerSpaceFor(Plan plan) {
		return plan == Plan.PREMIUM
				? PREMIUM_TESTIMONIAL_LIMIT_PER_SPACE
				: FREE_TESTIMONIAL_LIMIT_PER_SPACE;
	}

	/**
	 * The user's subscription, if they have one.
	 */
	@Transactional(readOnly = true)
	public Optional<Subscription> subscriptionOf(UUID userId) {
		return subscriptionRepository.findByUserId(userId);
	}
}
