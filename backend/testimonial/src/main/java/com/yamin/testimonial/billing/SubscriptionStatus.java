package com.yamin.testimonial.billing;

/**
 * The lifecycle state of a user's subscription.
 *
 * <p>Kept separate from {@link Plan} because PRD 4.9 requires a past-due
 * account to keep Premium access while a final payment failure downgrades it
 * to Free. {@code plan=PREMIUM, status=PAST_DUE} is therefore a legitimate
 * combination, and the effective plan is derived from both -- see
 * {@link Subscription#isPremium()}.
 */
public enum SubscriptionStatus {

	/** Paid up and in good standing. */
	ACTIVE,

	/** A renewal failed but Stripe is still retrying. Premium access continues. */
	PAST_DUE,

	/** The subscription was cancelled or ended. */
	CANCELED,

	/** Payment never completed. */
	INCOMPLETE
}
