package com.yamin.testimonial.billing.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.billing.BillingException;
import com.yamin.testimonial.billing.Plan;
import com.yamin.testimonial.billing.PlanLimits;
import com.yamin.testimonial.billing.Subscription;
import com.yamin.testimonial.billing.SubscriptionService;
import com.yamin.testimonial.billing.config.StripeProperties;
import com.yamin.testimonial.common.exception.NotFoundException;

/**
 * The Billing page's API (PRD 4.9).
 *
 * <p>The page shows the current plan, the plans that can be chosen, the
 * subscription's status, and the actions: upgrade, cancel and resume.
 *
 * <p>None of these endpoints change the plan. The upgrade endpoint creates a
 * Stripe Checkout Session and the browser goes to Stripe; the plan changes
 * when Stripe sends a verified webhook (PRD 4.9).
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

	private final SubscriptionService subscriptionService;
	private final PlanLimits planLimits;
	private final AppUserRepository appUserRepository;
	private final StripeProperties stripeProperties;

	public BillingController(SubscriptionService subscriptionService, PlanLimits planLimits,
			AppUserRepository appUserRepository, StripeProperties stripeProperties) {

		this.subscriptionService = subscriptionService;
		this.planLimits = planLimits;
		this.appUserRepository = appUserRepository;
		this.stripeProperties = stripeProperties;
	}

	/**
	 * The user's billing state.
	 */
	@GetMapping
	public BillingDtos.BillingResponse get(@AuthenticationPrincipal UUID userId) {
		Subscription subscription = subscriptionService.subscriptionFor(userId);
		Plan plan = planLimits.planOf(userId);

		return new BillingDtos.BillingResponse(
				plan.name(),
				subscription.getStatus().name(),
				plan == Plan.PREMIUM,
				subscription.isCancelAtPeriodEnd(),
				subscription.getCurrentPeriodEnd(),
				subscription.getStripeCustomerId(),
				new BillingDtos.LimitsResponse(
						planLimits.spaceLimitFor(plan),
						planLimits.testimonialLimitPerSpaceFor(plan)),
				plansFor(plan));
	}

	/**
	 * Starts a checkout for the Premium plan.
	 *
	 * <p>The response is a URL the browser is sent to. Nothing about the
	 * user's plan changes here: only a verified webhook does that (PRD 4.9).
	 */
	@PostMapping("/checkout")
	public BillingDtos.CheckoutResponse checkout(@AuthenticationPrincipal UUID userId) {
		if (!stripeProperties.isConfigured()) {
			throw new BillingException(
					"Billing is not set up on this server, so the upgrade cannot be started.");
		}

		AppUser user = appUserRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("No such user"));

		String url = subscriptionService.createCheckoutSession(userId, user.getEmail());
		return new BillingDtos.CheckoutResponse(url);
	}

	/**
	 * Cancels the subscription at the end of the paid period.
	 *
	 * <p>Premium access continues until then (PRD 4.9).
	 */
	@PostMapping("/cancel")
	public BillingDtos.BillingResponse cancel(@AuthenticationPrincipal UUID userId) {
		subscriptionService.cancel(userId);
		return get(userId);
	}

	/**
	 * Undoes a cancellation (PRD 4.9).
	 */
	@PostMapping("/resume")
	public BillingDtos.BillingResponse resume(@AuthenticationPrincipal UUID userId) {
		subscriptionService.resume(userId);
		return get(userId);
	}

	/**
	 * The plans on offer, with their features (PRD 7).
	 */
	private List<BillingDtos.PlanResponse> plansFor(Plan current) {
		return List.of(
				new BillingDtos.PlanResponse(
						Plan.FREE.name(),
						"Free",
						"$0",
						current == Plan.FREE,
						List.of(
								"3 Spaces",
								"100 testimonials per Space",
								"Basic themes",
								"Masonry embed layout")),
				new BillingDtos.PlanResponse(
						Plan.PREMIUM.name(),
						"Premium",
						"$9.99/month",
						current == Plan.PREMIUM,
						List.of(
								"25 Spaces",
								"1,000 testimonials per Space",
								"Basic and premium themes",
								"Masonry and Carousel embed layouts",
								"Embed background colour",
								"Embed animation")));
	}
}
