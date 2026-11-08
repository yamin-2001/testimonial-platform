package com.yamin.testimonial.billing;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.LiveStripeResponseGetter;
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionResumeParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.service.SubscriptionService;
import com.stripe.service.checkout.SessionService;
import com.yamin.testimonial.billing.config.StripeProperties;

/**
 * The Stripe implementation of {@link StripeGateway}.
 *
 * <p>The API key is passed per call rather than stored in the SDK's static
 * state, so it comes from the configuration and is never hard-coded (PRD 8).
 */
@Component
public class StripeGatewayImpl implements StripeGateway {

	/** The metadata key the webhook uses to find the user again. */
	public static final String METADATA_USER_ID = "userId";

	private final StripeProperties properties;
	private final SessionService checkoutSessions;
	private final SubscriptionService subscriptions;

	public StripeGatewayImpl(StripeProperties properties) {
		this.properties = properties;

		LiveStripeResponseGetter responses = new LiveStripeResponseGetter();
		this.checkoutSessions = new SessionService(responses);
		this.subscriptions = new SubscriptionService(responses);
	}

	@Override
	public String createCheckoutSession(String customerEmail, String userId) {
		SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
				.setPrice(properties.getPremiumPriceId())
				.setQuantity(1L)
				.build();

		SessionCreateParams params = SessionCreateParams.builder()
				.setMode(SessionCreateParams.Mode.SUBSCRIPTION)
				.setCustomerEmail(customerEmail)
				.setClientReferenceId(userId)
				.setSuccessUrl(properties.getSuccessUrl())
				.setCancelUrl(properties.getCancelUrl())
				.putMetadata(METADATA_USER_ID, userId)
				.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
						.putMetadata(METADATA_USER_ID, userId)
						.build())
				.addLineItem(lineItem)
				.build();

		try {
			Session session = checkoutSessions.create(params, requestOptions());
			return session.getUrl();
		} catch (StripeException e) {
			throw new BillingException("Stripe could not start the checkout", e);
		}
	}

	@Override
	public void cancelAtPeriodEnd(String subscriptionId) {
		try {
			subscriptions.cancel(subscriptionId,
					SubscriptionCancelParams.builder().setInvoiceNow(false).build(),
					requestOptions());
		} catch (StripeException e) {
			throw new BillingException("Stripe could not cancel the subscription", e);
		}
	}

	@Override
	public void resume(String subscriptionId) {
		try {
			subscriptions.resume(subscriptionId, SubscriptionResumeParams.builder().build(),
					requestOptions());
		} catch (StripeException e) {
			throw new BillingException("Stripe could not resume the subscription", e);
		}
	}

	@Override
	public Optional<SubscriptionState> retrieve(String subscriptionId) {
		try {
			Subscription subscription = subscriptions.retrieve(subscriptionId, requestOptions());

			return Optional.of(new SubscriptionState(
					subscription.getStatus(),
					periodEndOf(subscription),
					Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())));
		} catch (StripeException e) {
			throw new BillingException("Stripe could not be read", e);
		}
	}

	/**
	 * The request options carrying the configured API key.
	 */
	private RequestOptions requestOptions() {
		return RequestOptions.builder()
				.setApiKey(properties.getSecretKey())
				.build();
	}

	/**
	 * The end of the paid period.
	 *
	 * <p>Stripe moved this onto the subscription's first item, so it is read
	 * from there.
	 */
	private Instant periodEndOf(Subscription subscription) {
		if (subscription.getItems() != null && subscription.getItems().getData() != null
				&& !subscription.getItems().getData().isEmpty()) {

			Long itemEnd = subscription.getItems().getData().get(0).getCurrentPeriodEnd();
			if (itemEnd != null) {
				return Instant.ofEpochSecond(itemEnd);
			}
		}

		return null;
	}
}
