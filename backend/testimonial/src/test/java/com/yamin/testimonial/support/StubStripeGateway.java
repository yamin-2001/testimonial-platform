package com.yamin.testimonial.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.yamin.testimonial.billing.StripeGateway;

/**
 * A Stripe gateway that records what it was asked to do.
 *
 * <p>The billing rules decide whether somebody has Premium, and those rules
 * must be tested without a Stripe account and without a network call. This
 * stub stands in for Stripe and records what it was asked, so a test can
 * assert on it.
 *
 * <p>The subscription states it reports are set by the test, because the
 * webhook reads the subscription's state from Stripe.
 */
public class StubStripeGateway implements StripeGateway {

	/** The user the last checkout was started for. */
	public String lastCheckoutUserId;

	/** The email the last checkout was started with. */
	public String lastCheckoutEmail;

	/** The subscription the last cancellation was for. */
	public String cancelledSubscriptionId;

	/** The subscription the last resume was for. */
	public String resumedSubscriptionId;

	/** The subscription the last retrieve was for. */
	public String retrievedSubscriptionId;

	/** The state the stub reports for a retrieve. */
	private SubscriptionState state = new SubscriptionState("active", null, false);

	/**
	 * The states the stub reports, keyed by subscription id.
	 */
	private final Map<String, SubscriptionState> states = new LinkedHashMap<>();

	/**
	 * Forgets everything that was recorded.
	 */
	public void reset() {
		lastCheckoutUserId = null;
		lastCheckoutEmail = null;
		cancelledSubscriptionId = null;
		resumedSubscriptionId = null;
		retrievedSubscriptionId = null;
		state = new SubscriptionState("active", null, false);
		states.clear();
	}

	/**
	 * Sets the state the stub reports for every subscription.
	 */
	public void reportState(String status, Instant currentPeriodEnd, boolean cancelAtPeriodEnd) {
		this.state = new SubscriptionState(status, currentPeriodEnd, cancelAtPeriodEnd);
	}

	/**
	 * Sets the state the stub reports for one subscription.
	 */
	public void reportStateFor(String subscriptionId, String status, Instant currentPeriodEnd,
			boolean cancelAtPeriodEnd) {

		states.put(subscriptionId,
				new SubscriptionState(status, currentPeriodEnd, cancelAtPeriodEnd));
	}

	@Override
	public String createCheckoutSession(String customerEmail, String userId) {
		lastCheckoutEmail = customerEmail;
		lastCheckoutUserId = userId;
		return "https://checkout.stripe.test/session";
	}

	@Override
	public void cancelAtPeriodEnd(String subscriptionId) {
		cancelledSubscriptionId = subscriptionId;
	}

	@Override
	public void resume(String subscriptionId) {
		resumedSubscriptionId = subscriptionId;
	}

	@Override
	public Optional<SubscriptionState> retrieve(String subscriptionId) {
		retrievedSubscriptionId = subscriptionId;

		SubscriptionState specific = states.get(subscriptionId);
		return Optional.of(specific != null ? specific : state);
	}
}
