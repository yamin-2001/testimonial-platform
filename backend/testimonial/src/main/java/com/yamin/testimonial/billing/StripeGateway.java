package com.yamin.testimonial.billing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The Stripe operations the application needs (PRD 4.9).
 *
 * <p>An interface rather than a direct use of the Stripe SDK, so the billing
 * logic can be tested without a Stripe account and without a network call.
 * The same pattern is used for the CAPTCHA verifier.
 *
 * <p>The names here are the application's own. The Stripe SDK's vocabulary
 * ({@code subscription}, {@code customer}, {@code checkout session}) is
 * translated at the edge, so a change in Stripe's API does not reach into the
 * subscription rules.
 */
public interface StripeGateway {

	/**
	 * Creates a Stripe Checkout Session for the Premium plan.
	 *
	 * @param customerEmail the email to pre-fill the checkout with
	 * @param userId the application's user id, sent to Stripe as the client
	 * reference id so the webhook can find the user again
	 * @return the URL the browser is redirected to
	 */
	String createCheckoutSession(String customerEmail, String userId);

	/**
	 * Cancels a subscription at the end of the paid period.
	 *
	 * <p>Premium access continues until the period ends (PRD 4.9).
	 */
	void cancelAtPeriodEnd(String subscriptionId);

	/**
	 * Undoes a cancellation, so the subscription continues (PRD 4.9).
	 */
	void resume(String subscriptionId);

	/**
	 * The current state of a subscription, as Stripe sees it.
	 */
	Optional<SubscriptionState> retrieve(String subscriptionId);

	/**
	 * A subscription's state, as the application needs it.
	 *
	 * @param status Stripe's status string, such as {@code active} or
	 * {@code past_due}
	 * @param currentPeriodEnd the end of the paid period
	 * @param cancelAtPeriodEnd whether the subscription is set to end at the
	 * close of the period
	 */
	record SubscriptionState(
			String status,
			Instant currentPeriodEnd,
			boolean cancelAtPeriodEnd) {
	}

	/**
	 * The Stripe event types the application acts on.
	 *
	 * <p>Named here so the webhook handler and the tests agree on them, and
	 * so an unhandled type is visibly unhandled rather than silently ignored.
	 */
	final class EventTypes {

		/** A checkout was completed. */
		public static final String CHECKOUT_COMPLETED = "checkout.session.completed";

		/** A subscription's state changed. */
		public static final String SUBSCRIPTION_UPDATED = "customer.subscription.updated";

		/** A subscription was deleted. */
		public static final String SUBSCRIPTION_DELETED = "customer.subscription.deleted";

		/** An invoice could not be paid; Stripe will retry. */
		public static final String INVOICE_PAYMENT_FAILED = "invoice.payment_failed";

		/** A payment succeeded. */
		public static final String INVOICE_PAYMENT_SUCCEEDED = "invoice.payment_succeeded";

		private EventTypes() {
		}

		/**
		 * Whether the application acts on an event type.
		 */
		public static boolean isHandled(String type) {
			return List.of(
					CHECKOUT_COMPLETED,
					SUBSCRIPTION_UPDATED,
					SUBSCRIPTION_DELETED,
					INVOICE_PAYMENT_FAILED,
					INVOICE_PAYMENT_SUCCEEDED).contains(type);
		}
	}
}
