package com.yamin.testimonial.billing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.common.exception.NotFoundException;
import com.yamin.testimonial.common.exception.ValidationException;

/**
 * The user's subscription, and the verified Stripe events that change it
 * (PRD 4.9).
 *
 * <p>Two rules shape this class.
 *
 * <p><strong>The subscription changes only from a verified webhook</strong>
 * (PRD 6.15). There is no other writer of {@link Subscription#getPlan()} and
 * {@link Subscription#getStatus()}. In particular the successful redirect
 * back from Stripe's checkout does not touch the plan -- that is the
 * acceptance criterion in PRD 4.9, and it is why the plan is not set here in
 * {@link #createCheckoutSession}.
 *
 * <p><strong>Processing an event twice does nothing the second time</strong>
 * (PRD 6.14). Stripe retries an event it thinks was not acknowledged, so the
 * same event id can arrive more than once. The event id is the primary key of
 * {@link ProcessedStripeEvent}, so a duplicate insert fails and the handler
 * treats it as "already handled".
 */
@Service
public class SubscriptionService {

	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionService.class);

	/**
	 * The Stripe status that means the subscription is in good standing.
	 */
	private static final String STRIPE_STATUS_ACTIVE = "active";

	/**
	 * The Stripe status that means a payment failed but Stripe is still
	 * retrying. Premium access continues (PRD 4.9).
	 */
	private static final String STRIPE_STATUS_PAST_DUE = "past_due";

	/**
	 * The Stripe statuses that mean there is no paid subscription.
	 */
	private static final String STRIPE_STATUS_CANCELED = "canceled";

	private static final String STRIPE_STATUS_INCOMPLETE = "incomplete";

	private static final String STRIPE_STATUS_UNPAID = "unpaid";

	private final SubscriptionRepository subscriptionRepository;
	private final ProcessedStripeEventRepository processedEvents;
	private final AppUserRepository appUserRepository;
	private final StripeGateway stripe;

	public SubscriptionService(SubscriptionRepository subscriptionRepository,
			ProcessedStripeEventRepository processedEvents,
			AppUserRepository appUserRepository,
			StripeGateway stripe) {

		this.subscriptionRepository = subscriptionRepository;
		this.processedEvents = processedEvents;
		this.appUserRepository = appUserRepository;
		this.stripe = stripe;
	}

	/**
	 * The user's subscription, creating a Free one if they have none.
	 *
	 * <p>A user who has never subscribed still has a subscription: it is the
	 * Free plan, active. Having the row means the webhook has somewhere to
	 * write, and the Billing page always has something to show.
	 */
	@Transactional
	public Subscription subscriptionFor(UUID userId) {
		return subscriptionRepository.findByUserId(userId)
				.orElseGet(() -> {
					AppUser owner = appUserRepository.findById(userId).orElse(null);
					if (owner == null) {
						// The event named a user this application does not know.
						// There is nothing to write to.
						return null;
					}

					Subscription subscription = new Subscription();
					subscription.setUser(owner);
					subscription.setPlan(Plan.FREE);
					subscription.setStatus(SubscriptionStatus.ACTIVE);
					return subscriptionRepository.save(subscription);
				});
	}

	/**
	 * Creates a Stripe Checkout Session for the Premium plan.
	 *
	 * <p>This does <em>not</em> change the plan. The plan changes when Stripe
	 * sends a verified webhook saying the checkout completed (PRD 4.9).
	 *
	 * @return the URL the browser is redirected to
	 */
	@Transactional
	public String createCheckoutSession(UUID userId, String email) {
		return stripe.createCheckoutSession(email, userId.toString());
	}

	/**
	 * Cancels the subscription at the end of the paid period.
	 *
	 * <p>Premium access continues until then (PRD 4.9). The flag is only set
	 * here; the plan itself changes when Stripe says the period has ended.
	 */
	@Transactional
	public void cancel(UUID userId) {
		Subscription subscription = requireSubscription(userId);

		if (subscription.getStripeSubscriptionId() == null) {
			throw new ValidationException("subscription",
					"There is no subscription to cancel.");
		}

		stripe.cancelAtPeriodEnd(subscription.getStripeSubscriptionId());
	}

	/**
	 * Undoes a cancellation, so the subscription continues (PRD 4.9).
	 */
	@Transactional
	public void resume(UUID userId) {
		Subscription subscription = requireSubscription(userId);

		if (subscription.getStripeSubscriptionId() == null) {
			throw new ValidationException("subscription",
					"There is no subscription to resume.");
		}

		stripe.resume(subscription.getStripeSubscriptionId());
	}

	/**
	 * Applies a verified Stripe event.
	 *
	 * <p>Idempotent: an event id that has already been handled is ignored, so
	 * a replayed event does not apply its effects twice (PRD 6.14).
	 *
	 * @param eventId the event's id
	 * @param eventType the event's type
	 * @param subscriptionId the subscription the event is about, if any
	 * @param customerId the customer the event is about, if any
	 * @param userId the application's user the event is about, if any
	 * @param status the subscription's status, as Stripe reports it
	 * @param currentPeriodEnd the end of the paid period
	 * @param cancelAtPeriodEnd whether the subscription ends at the close of
	 * the period
	 * @return whether the event was applied
	 */
	@Transactional
	public boolean applyEvent(String eventId, String eventType, String subscriptionId,
			String customerId, String userId, String status, Instant currentPeriodEnd,
			boolean cancelAtPeriodEnd) {

		if (alreadyProcessed(eventId)) {
			LOG.info("Stripe event {} was already handled; ignoring the replay", eventId);
			return false;
		}

		Subscription subscription = findSubscription(subscriptionId, customerId, userId);
		if (subscription != null) {
			// The Stripe ids are recorded as soon as they are known, so later
			// events can find this subscription by either of them.
			if (subscriptionId != null && subscription.getStripeSubscriptionId() == null) {
				subscription.setStripeSubscriptionId(subscriptionId);
			}
			if (customerId != null && subscription.getStripeCustomerId() == null) {
				subscription.setStripeCustomerId(customerId);
			}

			applyState(subscription, status, currentPeriodEnd, cancelAtPeriodEnd);
			subscriptionRepository.save(subscription);
		}

		recordProcessed(eventId, eventType);
		return true;
	}

	/**
	 * Whether the event has been handled before.
	 */
	@Transactional(readOnly = true)
	public boolean alreadyProcessed(String eventId) {
		return processedEvents.existsById(eventId);
	}

	/**
	 * Records that an event has been handled.
	 *
	 * <p>The event id is the primary key, so a concurrent duplicate fails with
	 * a constraint violation. That is the point: it is what makes a replayed
	 * event a no-op rather than a second application of the same change.
	 */
	private void recordProcessed(String eventId, String eventType) {
		ProcessedStripeEvent processed = new ProcessedStripeEvent(eventId, eventType,
				Instant.now());
		processedEvents.saveAndFlush(processed);
	}

	/**
	 * Finds the subscription an event is about.
	 *
	 * <p>Stripe identifies the subscription and the customer; the metadata
	 * carries the application's user id. Any one of the three is enough to
	 * find the row, because different event types carry different ones.
	 *
	 * <p>A user who has no subscription row yet gets one. The very first
	 * event a user receives is their checkout completing, and it must not be
	 * dropped for want of a row to write to.
	 */
	private Subscription findSubscription(String subscriptionId, String customerId,
			String userId) {

		if (subscriptionId != null) {
			Optional<Subscription> found =
					subscriptionRepository.findByStripeSubscriptionId(subscriptionId);
			if (found.isPresent()) {
				return found.get();
			}
		}

		if (customerId != null) {
			Optional<Subscription> found =
					subscriptionRepository.findByStripeCustomerId(customerId);
			if (found.isPresent()) {
				return found.get();
			}
		}

		if (userId != null) {
			try {
				UUID id = UUID.fromString(userId);
				return subscriptionFor(id);
			} catch (IllegalArgumentException e) {
				// Stripe metadata is not necessarily a UUID. It simply does not
				// identify a user here, which is not an error.
				return null;
			}
		}

		return null;
	}

	/**
	 * Writes the state Stripe reported onto the subscription.
	 *
	 * <p>A past-due subscription stays on the Premium plan: Stripe is still
	 * retrying and access continues until the final failure (PRD 4.9).
	 */
	private void applyState(Subscription subscription, String status,
			Instant currentPeriodEnd, boolean cancelAtPeriodEnd) {

		if (currentPeriodEnd != null) {
			subscription.setCurrentPeriodEnd(currentPeriodEnd);
		}

		subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);

		if (status == null) {
			// The event said nothing about the status, so it is left alone.
			return;
		}

		switch (status) {
			case STRIPE_STATUS_ACTIVE -> {
				subscription.setPlan(Plan.PREMIUM);
				subscription.setStatus(SubscriptionStatus.ACTIVE);
			}
			case STRIPE_STATUS_PAST_DUE -> {
				// Still Premium: Stripe is still retrying the payment.
				subscription.setPlan(Plan.PREMIUM);
				subscription.setStatus(SubscriptionStatus.PAST_DUE);
			}
			case STRIPE_STATUS_CANCELED, STRIPE_STATUS_UNPAID -> {
				// The subscription has ended for good.
				subscription.setPlan(Plan.FREE);
				subscription.setStatus(SubscriptionStatus.CANCELED);
			}
			case STRIPE_STATUS_INCOMPLETE -> {
				// Payment never completed, so there is no Premium to give.
				subscription.setPlan(Plan.FREE);
				subscription.setStatus(SubscriptionStatus.INCOMPLETE);
			}
			default -> {
				// A status the application does not know about is left as it is,
				// rather than guessed at. Freeing a plan on an unknown status
				// could take away access somebody has paid for.
				LOG.info("Stripe reported an unhandled subscription status: {}", status);
			}
		}
	}

	/**
	 * The user's subscription, or a failure if they have none.
	 */
	private Subscription requireSubscription(UUID userId) {
		return subscriptionRepository.findByUserId(userId)
				.orElseThrow(() -> new NotFoundException("No subscription found"));
	}

	/**
	 * The Stripe customer id on the user's subscription, if there is one.
	 */
	@Transactional(readOnly = true)
	public Optional<String> customerIdOf(UUID userId) {
		return subscriptionRepository.findByUserId(userId)
				.map(Subscription::getStripeCustomerId);
	}

	/**
	 * The metadata Stripe sends with a checkout session.
	 */
	public static Map<String, String> metadataOf(Map<String, String> metadata) {
		return metadata == null ? Map.of() : metadata;
	}
}
