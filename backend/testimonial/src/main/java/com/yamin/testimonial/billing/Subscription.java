package com.yamin.testimonial.billing;

import java.time.Instant;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.common.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * A user's subscription state.
 *
 * <p>One row per user. {@code plan} and {@code status} are both stored because
 * PRD 4.9 treats a past-due account as still Premium until Stripe gives up
 * on the final retry.
 *
 * <p>The verified Stripe webhook is the only writer of both columns, so they
 * cannot drift apart (PRD 6.15). A successful checkout redirect never changes
 * the plan on its own (PRD 4.9).
 */
@Entity
@Table(name = "subscriptions")
public class Subscription extends AuditableEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Enumerated(EnumType.STRING)
	@Column(name = "plan", nullable = false)
	private Plan plan = Plan.FREE;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

	@Column(name = "stripe_customer_id")
	private String stripeCustomerId;

	@Column(name = "stripe_subscription_id")
	private String stripeSubscriptionId;

	/** End of the paid period. Shown on the Billing page (PRD 4.9). */
	@Column(name = "current_period_end")
	private Instant currentPeriodEnd;

	/** True when the user cancelled but Premium continues until the period ends. */
	@Column(name = "cancel_at_period_end", nullable = false)
	private boolean cancelAtPeriodEnd = false;

	public AppUser getUser() {
		return user;
	}

	public void setUser(AppUser user) {
		this.user = user;
	}

	public Plan getPlan() {
		return plan;
	}

	public void setPlan(Plan plan) {
		this.plan = plan;
	}

	public SubscriptionStatus getStatus() {
		return status;
	}

	public void setStatus(SubscriptionStatus status) {
		this.status = status;
	}

	public String getStripeCustomerId() {
		return stripeCustomerId;
	}

	public void setStripeCustomerId(String stripeCustomerId) {
		this.stripeCustomerId = stripeCustomerId;
	}

	public String getStripeSubscriptionId() {
		return stripeSubscriptionId;
	}

	public void setStripeSubscriptionId(String stripeSubscriptionId) {
		this.stripeSubscriptionId = stripeSubscriptionId;
	}

	public Instant getCurrentPeriodEnd() {
		return currentPeriodEnd;
	}

	public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
		this.currentPeriodEnd = currentPeriodEnd;
	}

	public boolean isCancelAtPeriodEnd() {
		return cancelAtPeriodEnd;
	}

	public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
		this.cancelAtPeriodEnd = cancelAtPeriodEnd;
	}

	/**
	 * Whether this subscription currently grants Premium access.
	 *
	 * <p>This is the single place plan limits should be derived from. Reading
	 * {@link #getPlan()} directly would ignore the past-due grace period that
	 * PRD 4.9 requires, and would treat a cancelled subscription as Premium.
	 *
	 * <p>{@link SubscriptionStatus#PAST_DUE} deliberately counts as Premium:
	 * Stripe is still retrying and access continues until the final failure.
	 */
	public boolean isPremium() {
		return plan == Plan.PREMIUM
				&& status != SubscriptionStatus.CANCELED
				&& status != SubscriptionStatus.INCOMPLETE;
	}
}
