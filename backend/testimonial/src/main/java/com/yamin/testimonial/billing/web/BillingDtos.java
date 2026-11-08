package com.yamin.testimonial.billing.web;

import java.time.Instant;
import java.util.List;

/**
 * The shapes the billing API returns (PRD 4.9).
 */
public final class BillingDtos {

	private BillingDtos() {
	}

	/**
	 * The user's billing state.
	 *
	 * @param plan {@code FREE} or {@code PREMIUM}
	 * @param status the subscription's status
	 * @param premium whether the user currently has Premium access
	 * @param cancelAtPeriodEnd whether the subscription ends at the close of
	 * the paid period
	 * @param currentPeriodEnd the end of the paid period
	 * @param stripeCustomerId the Stripe customer, if there is one
	 * @param limits the limits the current plan imposes
	 * @param plans the plans that can be chosen, with their features
	 */
	public record BillingResponse(
			String plan,
			String status,
			boolean premium,
			boolean cancelAtPeriodEnd,
			Instant currentPeriodEnd,
			String stripeCustomerId,
			LimitsResponse limits,
			List<PlanResponse> plans) {
	}

	/**
	 * The limits a plan imposes (PRD 7).
	 */
	public record LimitsResponse(
			int spaces,
			int testimonialsPerSpace) {
	}

	/**
	 * One plan, with the features it includes.
	 *
	 * @param key {@code FREE} or {@code PREMIUM}
	 * @param label the plan's name
	 * @param price the monthly price, in dollars
	 * @param current whether this is the user's current plan
	 * @param features the features the plan includes, as short labels
	 */
	public record PlanResponse(
			String key,
			String label,
			String price,
			boolean current,
			List<String> features) {
	}

	/**
	 * The answer to a request to start a checkout.
	 *
	 * @param url the Stripe-hosted checkout URL
	 */
	public record CheckoutResponse(String url) {
	}
}
