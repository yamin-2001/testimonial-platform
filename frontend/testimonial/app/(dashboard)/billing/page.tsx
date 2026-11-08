import { apiFetchWithSession } from "@/lib/session";
import { BillingState } from "@/lib/billing";

import { BillingPanel } from "@/components/billing-panel";

/**
 * The Billing page (PRD 4.9).
 *
 * It shows the current plan, the plans that can be chosen with their
 * features and limits, and the subscription's status -- including a
 * subscription that is set to end and one whose payment is late.
 *
 * The state comes from the backend, which is the only thing that decides
 * whether an account has Premium (PRD 6.15).
 */
export default async function BillingPage() {
  const state = await apiFetchWithSession<BillingState>("/api/billing");

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Billing
        </h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Your plan, and what it lets you do.
        </p>
      </div>

      <BillingPanel initialState={state} />
    </div>
  );
}
