package com.yamin.testimonial.spaces;

import com.yamin.testimonial.billing.Plan;

/**
 * Raised when a request would exceed the user's plan limit (PRD 4.2, 4.9).
 *
 * <p>Carries the limit that was reached so the UI can tell the user what to
 * upgrade and to what.
 */
public class PlanLimitException extends RuntimeException {

	private final int limit;

	private final Plan plan;

	public PlanLimitException(int limit, Plan plan) {
		super(plan == Plan.PREMIUM
				? "You have reached the limit of " + limit + " Spaces on your plan"
				: "You have reached the limit of " + limit + " Spaces on the Free plan. Upgrade to Premium to create more.");
		this.limit = limit;
		this.plan = plan;
	}

	/** The limit that was reached. */
	public int getLimit() {
		return limit;
	}

	/** The plan the limit belongs to. */
	public Plan getPlan() {
		return plan;
	}
}
