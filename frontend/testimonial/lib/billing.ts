import { apiFetch } from "./api";

/**
 * The user's billing state, as the Billing page shows it (PRD 4.9).
 *
 * Mirrors `BillingDtos.BillingResponse` on the Java side.
 */
export interface BillingState {
  /** "FREE" or "PREMIUM". */
  plan: string;
  /** "ACTIVE", "PAST_DUE", "CANCELED" or "INCOMPLETE". */
  status: string;
  /** Whether the account currently has Premium access. */
  premium: boolean;
  /** Whether the subscription ends at the close of the paid period. */
  cancelAtPeriodEnd: boolean;
  /** The end of the paid period, as an ISO timestamp. */
  currentPeriodEnd: string | null;
  /** The Stripe customer, if there is one. */
  stripeCustomerId: string | null;
  /** The limits the current plan imposes. */
  limits: BillingLimits;
  /** The plans that can be chosen. */
  plans: BillingPlan[];
}

/**
 * The limits a plan imposes (PRD 7).
 */
export interface BillingLimits {
  spaces: number;
  testimonialsPerSpace: number;
}

/**
 * One plan, with the features it includes.
 */
export interface BillingPlan {
  /** "FREE" or "PREMIUM". */
  key: string;
  label: string;
  /** The price, as a label such as "$9.99/month". */
  price: string;
  /** Whether this is the user's current plan. */
  current: boolean;
  /** The features the plan includes. */
  features: string[];
}

/**
 * Reads the user's billing state.
 */
export function getBillingState(): Promise<BillingState> {
  return apiFetch<BillingState>("/api/billing");
}

/**
 * Starts a checkout for the Premium plan.
 *
 * The returned URL is Stripe's hosted checkout page. Nothing about the plan
 * changes until Stripe sends a verified webhook, so the browser is simply
 * sent there (PRD 4.9).
 */
export async function startCheckout(): Promise<string> {
  const response = await apiFetch<{ url: string }>("/api/billing/checkout", {
    method: "POST",
  });
  return response.url;
}

/**
 * Cancels the subscription at the end of the paid period.
 *
 * Premium access continues until then (PRD 4.9).
 */
export function cancelSubscription(): Promise<BillingState> {
  return apiFetch<BillingState>("/api/billing/cancel", { method: "POST" });
}

/**
 * Undoes a cancellation, so the subscription continues (PRD 4.9).
 */
export function resumeSubscription(): Promise<BillingState> {
  return apiFetch<BillingState>("/api/billing/resume", { method: "POST" });
}
