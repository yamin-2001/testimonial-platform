"use client";

import { useState } from "react";

import { ApiError } from "@/lib/api";
import {
  BillingState,
  cancelSubscription,
  resumeSubscription,
  startCheckout,
} from "@/lib/billing";

/**
 * The Billing page's interactive part (PRD 4.9).
 *
 * It shows the current plan, the plans that can be chosen, and the actions:
 * upgrade, cancel and resume.
 *
 * The Upgrade button sends the browser to Stripe's hosted checkout. That
 * redirect does not change the plan -- the plan changes when Stripe sends a
 * verified webhook, which the page picks up the next time it is read. So the
 * page says so plainly rather than pretending the upgrade is already done.
 */
export function BillingPanel({
  initialState,
}: {
  initialState: BillingState | null;
}) {
  const [state, setState] = useState<BillingState | null>(initialState);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  /**
   * Starts the checkout and sends the browser to Stripe.
   */
  async function onUpgrade() {
    setBusy(true);
    setError(null);
    setMessage(null);

    try {
      const url = await startCheckout();
      window.location.href = url;
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "The upgrade could not be started. Please try again.",
      );
      setBusy(false);
    }
  }

  /**
   * Cancels the subscription at the end of the paid period.
   */
  async function onCancel() {
    setBusy(true);
    setError(null);
    setMessage(null);

    try {
      setState(await cancelSubscription());
      setMessage(
        "Your subscription will end at the close of the paid period. Premium access continues until then.",
      );
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "The subscription could not be cancelled. Please try again.",
      );
    }
    setBusy(false);
  }

  /**
   * Undoes a cancellation.
   */
  async function onResume() {
    setBusy(true);
    setError(null);
    setMessage(null);

    try {
      setState(await resumeSubscription());
      setMessage("Your subscription will continue as normal.");
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "The subscription could not be resumed. Please try again.",
      );
    }
    setBusy(false);
  }

  if (!state) {
    return (
      <p className="rounded-xl border border-zinc-200 px-4 py-3 text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
        The billing details could not be loaded. Please try again.
      </p>
    );
  }

  const plan = state.plans.find((candidate) => candidate.current);

  return (
    <div className="space-y-8">
      {state.status === "PAST_DUE" ? (
        <div
          role="status"
          data-testid="billing-past-due"
          className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
        >
          <p className="font-medium">Your last payment did not go through.</p>
          <p className="mt-1">
            We are retrying the payment. Your Premium access continues in the
            meantime.
          </p>
        </div>
      ) : null}

      {state.cancelAtPeriodEnd ? (
        <div
          role="status"
          data-testid="billing-cancel-scheduled"
          className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
        >
          <p className="font-medium">Your subscription is set to end.</p>
          <p className="mt-1">
            {state.currentPeriodEnd ? (
              <>
                It ends on{" "}
                <time dateTime={state.currentPeriodEnd}>
                  {new Date(state.currentPeriodEnd).toLocaleDateString("en-GB", {
                    day: "numeric",
                    month: "long",
                    year: "numeric",
                  })}
                </time>
                . Premium access continues until then.
              </>
            ) : (
              "Premium access continues until the end of the paid period."
            )}
          </p>
        </div>
      ) : null}

      <section aria-labelledby="plan-heading">
        <h2
          id="plan-heading"
          className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
        >
          Your plan
        </h2>

        <div className="mt-3 rounded-xl border border-zinc-200 px-4 py-4 dark:border-zinc-800">
          <div className="flex flex-wrap items-baseline justify-between gap-3">
            <p className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
              {plan?.label ?? "Free"}
              {state.premium ? (
                <span
                  data-testid="billing-premium-badge"
                  className="ml-2 rounded-full bg-zinc-900 px-2 py-0.5 align-middle text-xs font-medium text-white dark:bg-zinc-50 dark:text-zinc-900"
                >
                  Premium
                </span>
              ) : null}
            </p>
            <p className="text-sm text-zinc-500 dark:text-zinc-400">
              {plan?.price ?? "$0"}
            </p>
          </div>

          <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
            You can own {state.limits.spaces} Spaces and collect up to{" "}
            {state.limits.testimonialsPerSpace.toLocaleString("en-GB")}{" "}
            testimonials in each.
          </p>

          {state.premium && !state.cancelAtPeriodEnd ? (
            <div className="mt-4">
              <button
                type="button"
                onClick={onCancel}
                disabled={busy}
                data-testid="billing-cancel"
                className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 disabled:opacity-60 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
              >
                {busy ? "Working…" : "Cancel subscription"}
              </button>
            </div>
          ) : null}

          {state.cancelAtPeriodEnd ? (
            <div className="mt-4">
              <button
                type="button"
                onClick={onResume}
                disabled={busy}
                data-testid="billing-resume"
                className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 disabled:opacity-60 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
              >
                {busy ? "Working…" : "Keep my subscription"}
              </button>
            </div>
          ) : null}
        </div>
      </section>

      {!state.premium ? (
        <section aria-labelledby="plans-heading">
          <h2
            id="plans-heading"
            className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
          >
            Upgrade
          </h2>

          <div className="mt-3 grid gap-4 sm:grid-cols-2">
            {state.plans.map((candidate) => (
              <div
                key={candidate.key}
                data-testid={`billing-plan-${candidate.key.toLowerCase()}`}
                className={`rounded-xl border p-4 ${
                  candidate.key === "PREMIUM"
                    ? "border-zinc-900 ring-1 ring-zinc-900 dark:border-zinc-50 dark:ring-zinc-50"
                    : "border-zinc-200 dark:border-zinc-800"
                }`}
              >
                <div className="flex items-baseline justify-between gap-3">
                  <p className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
                    {candidate.label}
                  </p>
                  <p className="text-sm text-zinc-500 dark:text-zinc-400">
                    {candidate.price}
                  </p>
                </div>

                <ul className="mt-3 space-y-1.5">
                  {candidate.features.map((feature) => (
                    <li
                      key={feature}
                      className="flex items-start gap-2 text-sm text-zinc-600 dark:text-zinc-400"
                    >
                      <span aria-hidden="true" className="text-emerald-600">
                        ✓
                      </span>
                      {feature}
                    </li>
                  ))}
                </ul>

                {candidate.key === "PREMIUM" ? (
                  <button
                    type="button"
                    onClick={onUpgrade}
                    disabled={busy}
                    data-testid="billing-upgrade"
                    className="mt-4 w-full rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-zinc-700 disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
                  >
                    {busy ? "Working…" : "Upgrade to Premium"}
                  </button>
                ) : null}
              </div>
            ))}
          </div>
        </section>
      ) : null}

      {message ? (
        <p
          role="status"
          data-testid="billing-message"
          className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300"
        >
          {message}
        </p>
      ) : null}

      {error ? (
        <p
          role="alert"
          data-testid="billing-error"
          className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-300"
        >
          {error}
        </p>
      ) : null}
    </div>
  );
}
