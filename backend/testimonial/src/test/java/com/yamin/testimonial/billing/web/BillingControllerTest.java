package com.yamin.testimonial.billing.web;

import static org.assertj.core.api.Assertions.assertThat;


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
import com.yamin.testimonial.auth.AuthService;
import com.yamin.testimonial.billing.Plan;
import com.yamin.testimonial.billing.Subscription;
import com.yamin.testimonial.billing.SubscriptionRepository;
import com.yamin.testimonial.billing.SubscriptionStatus;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.support.StubStripeGateway;
import com.yamin.testimonial.support.StubStripeGatewayConfiguration;

/**
 * The billing API over HTTP (PRD 4.9).
 *
 * <p>The rules are covered by {@code SubscriptionServiceTest}. This test is
 * about the contract the Billing page depends on: what the page reads, and
 * the fact that starting a checkout does not change the plan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(StubStripeGatewayConfiguration.class)
@ActiveProfiles("test")
class BillingControllerTest extends PostgresTestContainer {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private AuthService authService;

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
	@DisplayName("the billing state needs a session")
	void theBillingStateNeedsASession() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/billing", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("a Free user sees the Free plan and both plans to choose from")
	void aFreeUserSeesTheFreePlanAndBothPlans() {
		ResponseEntity<String> response = get("/api/billing");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"plan\":\"FREE\"");
		assertThat(response.getBody()).contains("\"premium\":false");
		assertThat(response.getBody()).contains("\"label\":\"Premium\"");
		assertThat(response.getBody()).contains("\"spaces\":3");
		assertThat(response.getBody()).contains("\"testimonialsPerSpace\":100");
	}

	@Test
	@DisplayName("a Premium user sees the Premium plan and its limits")
	void aPremiumUserSeesThePremiumPlanAndItsLimits() {
		Subscription subscription = new Subscription();
		subscription.setUser(user);
		subscription.setPlan(Plan.PREMIUM);
		subscription.setStatus(SubscriptionStatus.ACTIVE);
		subscriptionRepository.save(subscription);

		ResponseEntity<String> response = get("/api/billing");

		assertThat(response.getBody()).contains("\"plan\":\"PREMIUM\"");
		assertThat(response.getBody()).contains("\"premium\":true");
		assertThat(response.getBody()).contains("\"spaces\":25");
		assertThat(response.getBody()).contains("\"testimonialsPerSpace\":1000");
	}

	@Test
	@DisplayName("a cancelled subscription shows the end of the paid period")
	void aCancelledSubscriptionShowsTheEndOfThePaidPeriod() {
		Subscription subscription = new Subscription();
		subscription.setUser(user);
		subscription.setPlan(Plan.PREMIUM);
		subscription.setStatus(SubscriptionStatus.ACTIVE);
		subscription.setCancelAtPeriodEnd(true);
		subscription.setCurrentPeriodEnd(java.time.Instant.parse("2026-12-01T00:00:00Z"));
		subscriptionRepository.save(subscription);

		ResponseEntity<String> response = get("/api/billing");

		assertThat(response.getBody()).contains("\"cancelAtPeriodEnd\":true");
		assertThat(response.getBody()).contains("2026-12-01T00:00:00Z");
		// Premium continues until the period ends (PRD 4.9).
		assertThat(response.getBody()).contains("\"premium\":true");
	}

	@Test
	@DisplayName("starting a checkout does not change the plan")
	void startingACheckoutDoesNotChangeThePlan() {
		// The Billing page has been read, so the account has a subscription.
		get("/api/billing");

		ResponseEntity<String> response = post("/api/billing/checkout");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("https://checkout.stripe.test/session");

		// The redirect to Stripe's checkout is not what upgrades the account
		// (PRD 4.9). Only a verified webhook does that.
		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);

		// And the page still says the account is Free.
		assertThat(get("/api/billing").getBody()).contains("\"plan\":\"FREE\"");
	}

	@Test
	@DisplayName("starting a checkout needs a session")
	void startingACheckoutNeedsASession() {
		ResponseEntity<String> response = restTemplate.exchange("/api/billing/checkout",
				HttpMethod.POST, new HttpEntity<>("{}", jsonHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("cancelling asks Stripe to stop at the end of the period")
	void cancellingAsksStripeToStopAtTheEndOfThePeriod() {
		Subscription subscription = new Subscription();
		subscription.setUser(user);
		subscription.setPlan(Plan.PREMIUM);
		subscription.setStatus(SubscriptionStatus.ACTIVE);
		subscription.setStripeSubscriptionId("sub_test_cancel");
		subscriptionRepository.save(subscription);

		ResponseEntity<String> response = post("/api/billing/cancel");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(stripe.cancelledSubscriptionId).isEqualTo("sub_test_cancel");
	}

	@Test
	@DisplayName("resuming asks Stripe to continue")
	void resumingAsksStripeToContinue() {
		Subscription subscription = new Subscription();
		subscription.setUser(user);
		subscription.setPlan(Plan.PREMIUM);
		subscription.setStatus(SubscriptionStatus.ACTIVE);
		subscription.setStripeSubscriptionId("sub_test_resume");
		subscriptionRepository.save(subscription);

		ResponseEntity<String> response = post("/api/billing/resume");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(stripe.resumedSubscriptionId).isEqualTo("sub_test_resume");
	}

	/**
	 * A GET with the owner's session.
	 */
	private ResponseEntity<String> get(String path) {
		return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(ownerHeaders()),
				String.class);
	}

	/**
	 * A POST with the owner's session.
	 */
	private ResponseEntity<String> post(String path) {
		return restTemplate.exchange(path, HttpMethod.POST,
				new HttpEntity<>("{}", jsonHeaders(ownerHeaders())), String.class);
	}

	/**
	 * The request headers that carry the owner's session.
	 */
	private HttpHeaders ownerHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add(HttpHeaders.COOKIE, "session=" + accessTokenFor(user));
		return headers;
	}

	/**
	 * Copies the headers and makes sure the content type is JSON.
	 */
	private HttpHeaders jsonHeaders() {
		return jsonHeaders(new HttpHeaders());
	}

	/**
	 * Copies the headers and makes sure the content type is JSON.
	 */
	private HttpHeaders jsonHeaders(HttpHeaders headers) {
		HttpHeaders copy = new HttpHeaders();
		copy.putAll(headers);
		copy.setContentType(MediaType.APPLICATION_JSON);
		return copy;
	}

	/**
	 * Issues an access token for a user, the way a sign-in would.
	 */
	private String accessTokenFor(AppUser user) {
		return authService.issueSession(user.getId()).accessToken();
	}
}
