package com.yamin.testimonial.billing.web;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.model.Event;
import com.yamin.testimonial.billing.StripeGateway;
import com.yamin.testimonial.billing.StripeGatewayImpl;
import com.yamin.testimonial.billing.SubscriptionService;
import com.yamin.testimonial.billing.config.StripeProperties;

/**
 * The endpoint Stripe sends subscription events to (PRD 4.9).
 *
 * <p>Two rules shape it.
 *
 * <p><strong>The signature is verified before anything is read.</strong> An
 * event that is not signed by Stripe is refused with a 400 and changes
 * nothing. This is what stops anybody who finds the URL from granting
 * themselves Premium (PRD 6.15).
 *
 * <p><strong>An event is handled once.</strong> Stripe retries an event it
 * thinks was not acknowledged, so the same event id can arrive twice. The
 * handler records the id, so a replay changes nothing (PRD 6.14).
 *
 * <p>The endpoint takes no session: Stripe is not a user of this application
 * and has no cookie to send. The signature is what authenticates it.
 */
@RestController
@RequestMapping("/api/billing/webhooks/stripe")
public class StripeWebhookController {

	private static final Logger LOG = LoggerFactory.getLogger(StripeWebhookController.class);

	/** The header Stripe signs its requests with. */
	private static final String SIGNATURE_HEADER = "Stripe-Signature";

	private final StripeProperties properties;
	private final SubscriptionService subscriptionService;
	private final StripeGateway stripe;

	public StripeWebhookController(StripeProperties properties,
			SubscriptionService subscriptionService,
			StripeGateway stripe) {

		this.properties = properties;
		this.subscriptionService = subscriptionService;
		this.stripe = stripe;
	}

	/**
	 * Handles a Stripe event.
	 *
	 * @param payload the raw request body, which is what the signature covers
	 * @param signature the {@code Stripe-Signature} header
	 */
	@PostMapping
	public ResponseEntity<Map<String, Object>> handle(
			@RequestBody String payload,
			@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {

		if (!properties.hasWebhookSecret()) {
			// Without a signing secret the signature cannot be checked, and an
			// unverified event must never change a subscription (PRD 6.15).
			LOG.error("A Stripe webhook arrived but no webhook secret is configured; refusing it");
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(body("Webhook signing is not configured"));
		}

		if (signature == null || signature.isBlank()) {
			return ResponseEntity.badRequest().body(body("Missing signature"));
		}

		Event event;
		try {
			event = Webhook.constructEvent(payload, signature, properties.getWebhookSecret());
		} catch (SignatureVerificationException e) {
			LOG.warn("A Stripe webhook with a bad signature was refused");
			return ResponseEntity.badRequest().body(body("Invalid signature"));
		}

		try {
			boolean applied = dispatch(event);
			return ResponseEntity.ok(body("ok"));
		} catch (RuntimeException e) {
			// A 500 makes Stripe retry, which is what should happen if the
			// database was briefly unavailable. The event id is not recorded
			// until the work is done, so a retry is not lost.
			LOG.error("A Stripe webhook could not be handled", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(body("The event could not be handled"));
		}
	}

	/**
	 * Applies one event.
	 *
	 * @return whether the event was applied rather than ignored
	 */
	private boolean dispatch(Event event) {
		String type = event.getType();

		if (!StripeGateway.EventTypes.isHandled(type)) {
			// An event the application does not act on is still recorded, so
			// a replay of it is not processed twice either.
			return subscriptionService.applyEvent(event.getId(), type, null, null, null, null,
					null, false);
		}

		return switch (type) {
			case StripeGateway.EventTypes.CHECKOUT_COMPLETED -> onCheckoutCompleted(event);
			case StripeGateway.EventTypes.SUBSCRIPTION_UPDATED -> onSubscriptionChanged(event);
			case StripeGateway.EventTypes.SUBSCRIPTION_DELETED -> onSubscriptionChanged(event);
			case StripeGateway.EventTypes.INVOICE_PAYMENT_FAILED -> onInvoice(event, false);
			case StripeGateway.EventTypes.INVOICE_PAYMENT_SUCCEEDED -> onInvoice(event, true);
			default -> subscriptionService.applyEvent(event.getId(), type, null, null, null, null,
					null, false);
		};
	}

	/**
	 * A checkout completed.
	 *
	 * <p>The plan is set here, and only here, from a verified event. The
	 * successful redirect back to the application does not change it
	 * (PRD 4.9).
	 */
	private boolean onCheckoutCompleted(Event event) {
		Object object = objectOf(event);
		if (!(object instanceof Session session)) {
			return subscriptionService.applyEvent(event.getId(), event.getType(), null, null,
					null, null, null, false);
		}

		String userId = userIdOf(session.getMetadata(), session.getClientReferenceId());
		String subscriptionId = session.getSubscription();
		String customerId = session.getCustomer();

		// The subscription is read from Stripe so the period end and the
		// status come from Stripe rather than being guessed at.
		String status = null;
		Instant periodEnd = null;
		boolean cancelAtPeriodEnd = false;

		if (subscriptionId != null) {
			var state = stripe.retrieve(subscriptionId);
			if (state.isPresent()) {
				status = state.get().status();
				periodEnd = state.get().currentPeriodEnd();
				cancelAtPeriodEnd = state.get().cancelAtPeriodEnd();
			}
		}

		return subscriptionService.applyEvent(event.getId(), event.getType(), subscriptionId,
				customerId, userId, status, periodEnd, cancelAtPeriodEnd);
	}

	/**
	 * A subscription was updated or deleted.
	 */
	private boolean onSubscriptionChanged(Event event) {
		Object object = objectOf(event);
		if (!(object instanceof Subscription subscription)) {
			return subscriptionService.applyEvent(event.getId(), event.getType(), null, null,
					null, null, null, false);
		}

		String status = subscription.getStatus();
		if (StripeGateway.EventTypes.SUBSCRIPTION_DELETED.equals(event.getType())) {
			// A deleted subscription has ended, whatever it said before.
			status = "canceled";
		}

		Instant periodEnd = null;
		if (subscription.getItems() != null && subscription.getItems().getData() != null
				&& !subscription.getItems().getData().isEmpty()) {

			Long end = subscription.getItems().getData().get(0).getCurrentPeriodEnd();
			if (end != null) {
				periodEnd = Instant.ofEpochSecond(end);
			}
		}

		return subscriptionService.applyEvent(event.getId(), event.getType(),
				subscription.getId(), subscription.getCustomer(),
				userIdOf(subscription.getMetadata(), null), status, periodEnd,
				Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
	}

	/**
	 * A payment failed, or succeeded again.
	 *
	 * <p>A failure makes the account past due, which still grants Premium
	 * access: Stripe retries the payment, and the account is only downgraded
	 * when Stripe gives up (PRD 4.9).
	 */
	private boolean onInvoice(Event event, boolean paid) {
		Object object = objectOf(event);
		if (!(object instanceof Invoice invoice)) {
			return subscriptionService.applyEvent(event.getId(), event.getType(), null, null,
					null, null, null, false);
		}

		String subscriptionId = subscriptionIdOf(invoice);
		String customerId = invoice.getCustomer();

		String status = paid ? "active" : "past_due";
		Instant periodEnd = null;

		if (subscriptionId != null) {
			var state = stripe.retrieve(subscriptionId);
			if (state.isPresent()) {
				status = state.get().status();
				periodEnd = state.get().currentPeriodEnd();
			}
		}

		return subscriptionService.applyEvent(event.getId(), event.getType(), subscriptionId,
				customerId, null, status, periodEnd, false);
	}

	/**
	 * The object the event is about.
	 */
	private Object objectOf(Event event) {
		if (event.getData() == null) {
			return null;
		}
		return event.getData().getObject();
	}

	/**
	 * The application's user id, from the metadata or the client reference.
	 */
	private String userIdOf(Map<String, String> metadata, String clientReferenceId) {
		if (metadata != null) {
			String fromMetadata = metadata.get(StripeGatewayImpl.METADATA_USER_ID);
			if (fromMetadata != null && !fromMetadata.isBlank()) {
				return fromMetadata;
			}
		}
		return clientReferenceId;
	}

	/**
	 * The subscription an invoice is about.
	 *
	 * <p>Newer Stripe API versions put it under the invoice's parent rather
	 * than directly on the invoice, so both are checked.
	 */
	private String subscriptionIdOf(Invoice invoice) {
		if (invoice.getParent() != null && invoice.getParent().getSubscriptionDetails() != null) {
			String id = invoice.getParent().getSubscriptionDetails().getSubscription();
			if (id != null && !id.isBlank()) {
				return id;
			}
		}

		return null;
	}

	private Map<String, Object> body(String message) {
		return Map.of("message", message);
	}
}
