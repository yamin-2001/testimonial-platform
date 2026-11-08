package com.yamin.testimonial.billing.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.billing.Plan;
import com.yamin.testimonial.billing.Subscription;
import com.yamin.testimonial.billing.SubscriptionRepository;
import com.yamin.testimonial.billing.SubscriptionStatus;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.support.StubStripeGateway;
import com.yamin.testimonial.support.StubStripeGatewayConfiguration;

/**
 * The Stripe webhook over HTTP (PRD 4.9, 6.14, 6.15).
 *
 * <p>The webhook is the only thing that can grant Premium, so the tests here
 * are about the trust boundary: a request with a bad signature must change
 * nothing, a request with no signature must change nothing, and the same
 * event delivered twice must be applied once.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(StubStripeGatewayConfiguration.class)
@ActiveProfiles("test")
class StripeWebhookControllerTest extends PostgresTestContainer {

	/** The signing secret from the test profile. */
	private static final String WEBHOOK_SECRET = "whsec_test_dummy";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private StubStripeGateway stripe;

	private AppUser user;

	@BeforeEach
	void reset() {
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
	@DisplayName("a checkout event with a valid signature makes the account Premium")
	void aValidSignatureUpgradesTheAccount() {
		String payload = """
				{
				  "id": "evt_upgrade",
				  "object": "event",
				  "type": "checkout.session.completed",
				  "data": {
				    "object": {
				      "id": "cs_test_1",
				      "object": "checkout.session",
				      "client_reference_id": "%s",
				      "customer": "cus_test_1",
				      "subscription": "sub_test_1",
				      "metadata": {"userId": "%s"}
				    }
				  }
				}
				""".formatted(user.getId(), user.getId());

		stripe.reportStateFor("sub_test_1", "active",
				Instant.now().plusSeconds(2_592_000), false);

		ResponseEntity<String> response = post(payload, sign(payload));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.PREMIUM);
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(subscription.getStripeSubscriptionId()).isEqualTo("sub_test_1");
	}

	@Test
	@DisplayName("a bad signature changes nothing")
	void aBadSignatureChangesNothing() {
		String payload = """
				{
				  "id": "evt_forged",
				  "object": "event",
				  "type": "checkout.session.completed",
				  "data": {
				    "object": {
					      "id": "cs_test_2",
					      "object": "checkout.session",
					      "client_reference_id": "%s",
					      "subscription": "sub_test_2",
					      "metadata": {"userId": "%s"}
				    }
				  }
				}
				""".formatted(user.getId(), user.getId());

		ResponseEntity<String> response = post(payload, "t=1,v1=not-a-real-signature");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		// Nothing was created and nothing was changed: the forged event was
		// never read, so it never reached the subscription.
		assertThat(subscriptionRepository.count()).isZero();
	}

	@Test
	@DisplayName("a missing signature changes nothing")
	void aMissingSignatureChangesNothing() {
		String payload = """
				{
				  "id": "evt_unsigned",
				  "object": "event",
				  "type": "checkout.session.completed",
				  "data": {"object": {"id": "cs_test_3", "object": "checkout.session"}}
				}
				""";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/billing/webhooks/stripe", HttpMethod.POST,
				new HttpEntity<>(payload, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		// Nothing was created and nothing was changed.
		assertThat(subscriptionRepository.count()).isZero();
	}

	@Test
	@DisplayName("the same event twice is applied once")
	void theSameEventTwiceIsAppliedOnce() {
		String payload = """
				{
				  "id": "evt_replayed",
				  "object": "event",
				  "type": "checkout.session.completed",
				  "data": {
				    "object": {
				      "id": "cs_test_4",
				      "object": "checkout.session",
				      "client_reference_id": "%s",
				      "subscription": "sub_test_4",
				      "metadata": {"userId": "%s"}
				    }
				  }
				}
				""".formatted(user.getId(), user.getId());

		stripe.reportStateFor("sub_test_4", "active", Instant.now().plusSeconds(2_592_000), false);

		post(payload, sign(payload));
		ResponseEntity<String> second = post(payload, sign(payload));

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

		// The subscription was set up once, so it is still exactly one row
		// with one subscription id.
		assertThat(subscriptionRepository.count()).isEqualTo(1);
		assertThat(subscriptionRepository.findByUserId(user.getId()).orElseThrow()
				.getStripeSubscriptionId()).isEqualTo("sub_test_4");
	}

	@Test
	@DisplayName("a subscription deleted event frees the account")
	void aSubscriptionDeletedEventFreesTheAccount() {
		String payload = """
				{
				  "id": "evt_deleted",
				  "object": "event",
				  "type": "customer.subscription.deleted",
				  "data": {
				    "object": {
					      "id": "sub_test_5",
					      "object": "subscription",
					      "customer": "cus_test_5",
					      "status": "canceled",
				      "cancel_at_period_end": false,
				      "metadata": {"userId": "%s"}
				    }
				  }
				}
				""".formatted(user.getId());

		post(payload, sign(payload));

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
	}

	@Test
	@DisplayName("a failed payment leaves the account Premium but past due")
	void aFailedPaymentLeavesTheAccountPremiumButPastDue() {
		// The invoice event carries the Stripe ids, not the user's, so the
		// subscription has to exist already -- which it does, because the
		// checkout completed before this event.
		Subscription existing = new Subscription();
		existing.setUser(user);
		existing.setPlan(Plan.PREMIUM);
		existing.setStatus(SubscriptionStatus.ACTIVE);
		existing.setStripeCustomerId("cus_test_6");
		existing.setStripeSubscriptionId("sub_test_6");
		subscriptionRepository.save(existing);

		String payload = """
				{
				  "id": "evt_failed",
				  "object": "event",
				  "type": "invoice.payment_failed",
				  "data": {
				    "object": {
				      "id": "in_test_1",
				      "object": "invoice",
				      "customer": "cus_test_6",
				      "parent": {
					        "type": "subscription",
				        "subscription_details": {"subscription": "sub_test_6"}
				      }
				    }
				  }
				}
				""";

		stripe.reportStateFor("sub_test_6", "past_due", Instant.now(), false);

		post(payload, sign(payload));

		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
		assertThat(subscription.isPremium()).isTrue();
	}

	/**
	 * Posts a payload with a signature header.
	 */
	private ResponseEntity<String> post(String payload, String signature) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Stripe-Signature", signature);

		return restTemplate.exchange("/api/billing/webhooks/stripe", HttpMethod.POST,
				new HttpEntity<>(payload, headers), String.class);
	}

	/**
	 * Signs a payload the way Stripe does.
	 *
	 * <p>The signature is the HMAC-SHA256 of {@code "<timestamp>.<payload>"},
	 * keyed by the signing secret, which is what Stripe's own verification
	 * expects.
	 */
	private String sign(String payload) {
		long timestamp = Instant.now().getEpochSecond();
		String signedPayload = timestamp + "." + payload;

		try {
			javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(
					WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
			mac.init(key);
			byte[] signature = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));

			return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(signature);
		} catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException("Could not sign the payload", e);
		}
	}
}
