package com.yamin.testimonial.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.common.exception.ValidationException;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.support.StubStripeGateway;
import com.yamin.testimonial.support.StubStripeGatewayConfiguration;

/**
 * The subscription rules (PRD 4.9).
 *
 * <p>These are the rules that decide whether somebody has Premium: which
 * event changes the plan, what a past-due account gets, when a cancellation
 * takes effect, and that a replayed event changes nothing.
 *
 * <p>The Stripe gateway is stubbed, so no test here reaches the network.
 */
@SpringBootTest
@Import(StubStripeGatewayConfiguration.class)
@ActiveProfiles("test")
class SubscriptionServiceTest extends PostgresTestContainer {

	@Autowired
	private SubscriptionService subscriptionService;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private ProcessedStripeEventRepository processedEvents;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private StubStripeGateway stripe;

	private AppUser user;

	@BeforeEach
	void reset() {
		processedEvents.deleteAll();
		subscriptionRepository.deleteAll();
		appUserRepository.deleteAll();

		user = new AppUser();
		user.setEmail("owner@example.com");
		user.setName("Owner");
		user.setPasswordHash("a-hash");
		user.setEmailVerified(true);
		user = appUserRepository.save(user);

		stripe.reset();
	}

	@Test
	@DisplayName("a user with no subscription has the Free plan")
	void aNewUserIsFree() {
		Subscription subscription = subscriptionService.subscriptionFor(user.getId());

		assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(subscription.isPremium()).isFalse();
	}

	@Test
	@DisplayName("a verified checkout event makes the account Premium")
	void aVerifiedCheckoutMakesTheAccountPremium() {
		subscriptionService.subscriptionFor(user.getId());

		boolean applied = subscriptionService.applyEvent(
				"evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now().plusSeconds(2_592_000), false);

		assertThat(applied).isTrue();

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.PREMIUM);
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(subscription.isPremium()).isTrue();
		assertThat(subscription.getStripeSubscriptionId()).isEqualTo("sub_123");
	}

	@Test
	@DisplayName("a replayed event does not apply the change twice")
	void aReplayedEventIsIgnored() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		// Stripe retries an event it thinks was not acknowledged, so the same
		// id arrives again (PRD 6.14).
		boolean applied = subscriptionService.applyEvent("evt_1",
				StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		assertThat(applied).isFalse();
		assertThat(processedEvents.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("a replayed event does not undo a later change")
	void aReplayedEventDoesNotUndoALaterChange() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		// The subscription is cancelled later on.
		subscriptionService.applyEvent("evt_2", StripeGateway.EventTypes.SUBSCRIPTION_DELETED,
				"sub_123", "cus_123", null,
				"canceled", Instant.now(), false);

		// The old event is replayed. It must not bring Premium back.
		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
	}

	@Test
	@DisplayName("a past-due account keeps Premium access")
	void aPastDueAccountKeepsPremium() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		// The renewal failed, but Stripe is still retrying (PRD 4.9).
		subscriptionService.applyEvent("evt_2", StripeGateway.EventTypes.INVOICE_PAYMENT_FAILED,
				"sub_123", "cus_123", null,
				"past_due", Instant.now(), false);

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
		assertThat(subscription.isPremium()).isTrue();
	}

	@Test
	@DisplayName("a cancelled subscription is Free")
	void aCancelledSubscriptionIsFree() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		subscriptionService.applyEvent("evt_2", StripeGateway.EventTypes.SUBSCRIPTION_DELETED,
				"sub_123", "cus_123", null,
				"canceled", Instant.now(), false);

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
		assertThat(subscription.isPremium()).isFalse();
	}

	@Test
	@DisplayName("a subscription cancelled at the period end stays Premium until the end")
	void aCancelledAtPeriodEndSubscriptionStaysPremium() {
		subscriptionService.subscriptionFor(user.getId());

		// The period end is stored with microsecond precision, so the instant
		// is given at that precision too.
		Instant periodEnd = Instant.now().plusSeconds(2_592_000).truncatedTo(
				java.time.temporal.ChronoUnit.MICROS);

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.SUBSCRIPTION_UPDATED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", periodEnd, true);

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.isCancelAtPeriodEnd()).isTrue();
		assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(periodEnd);
		assertThat(subscription.isPremium()).isTrue();
	}

	@Test
	@DisplayName("an incomplete payment gives no Premium")
	void anIncompletePaymentGivesNoPremium() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"incomplete", Instant.now(), false);

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.INCOMPLETE);
		assertThat(subscription.isPremium()).isFalse();
	}

	@Test
	@DisplayName("an unknown status leaves the plan alone")
	void anUnknownStatusLeavesThePlanAlone() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		subscriptionService.applyEvent("evt_2", StripeGateway.EventTypes.SUBSCRIPTION_UPDATED,
				"sub_123", "cus_123", null,
				"trialing", Instant.now(), false);

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.PREMIUM);
	}

	@Test
	@DisplayName("starting a checkout does not change the plan")
	void startingACheckoutDoesNotChangeThePlan() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.createCheckoutSession(user.getId(), user.getEmail());

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);
		assertThat(stripe.lastCheckoutUserId).isEqualTo(user.getId().toString());
	}

	@Test
	@DisplayName("cancelling asks Stripe to stop at the end of the period")
	void cancellingAsksStripeToStopAtTheEndOfThePeriod() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		subscriptionService.cancel(user.getId());

		assertThat(stripe.cancelledSubscriptionId).isEqualTo("sub_123");
	}

	@Test
	@DisplayName("resuming asks Stripe to continue")
	void resumingAsksStripeToContinue() {
		subscriptionService.subscriptionFor(user.getId());

		subscriptionService.applyEvent("evt_1", StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_123", "cus_123", user.getId().toString(),
				"active", Instant.now(), false);

		subscriptionService.resume(user.getId());

		assertThat(stripe.resumedSubscriptionId).isEqualTo("sub_123");
	}

	@Test
	@DisplayName("cancelling without a subscription is refused")
	void cancellingWithoutASubscriptionIsRefused() {
		subscriptionService.subscriptionFor(user.getId());

		assertThatThrownBy(() -> subscriptionService.cancel(user.getId()))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(
						((ValidationException) thrown).getFieldErrors().get("subscription"))
						.contains("no subscription"));
	}

	@Test
	@DisplayName("an event for an unknown user is recorded but changes nothing")
	void anEventForAnUnknownUserIsRecordedButChangesNothing() {
		subscriptionService.subscriptionFor(user.getId());

		boolean applied = subscriptionService.applyEvent("evt_1",
				StripeGateway.EventTypes.CHECKOUT_COMPLETED,
				"sub_999", "cus_999", null,
				"active", Instant.now(), false);

		assertThat(applied).isTrue();
		assertThat(processedEvents.existsById("evt_1")).isTrue();

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);
	}

	/**
	 * A Stripe gateway that records what it was asked to do.
	 */
}
