import { redirect } from "next/navigation";

import { SpaceWizard } from "@/components/space-wizard";
import { apiFetchWithSession, getCurrentUser } from "@/lib/session";
import { DashboardSummary, Theme } from "@/lib/spaces";

/**
 * The Space creation wizard (PRD 4.2).
 *
 * The flow is: title and subtitle, configure the fields, choose a theme,
 * ratings on or off, then the success page with the public URL.
 */
export default async function NewSpacePage() {
  const user = await getCurrentUser();
  if (!user) {
    redirect("/login");
  }

  // An unverified account may look around, but may not create Spaces
  // (PRD 4.1).
  if (!user.emailVerified) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Confirm your email address
        </h1>

        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          <p className="font-medium">
            You need a confirmed email address before you can create a Space.
          </p>
          <p className="mt-1">
            Open the verification link we emailed you, then come back here. If
            the link has expired, request a new one from the sign-in page.
          </p>
        </div>
      </div>
    );
  }

  const [themes, summary] = await Promise.all([
    apiFetchWithSession<{ themes: Theme[] }>("/api/spaces/themes"),
    apiFetchWithSession<DashboardSummary>("/api/spaces/dashboard/summary"),
  ]);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          New Space
        </h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Four quick steps and your public page is ready to share.
        </p>
      </div>

      <SpaceWizard themes={themes?.themes ?? []} premium={summary?.premium ?? false} />
    </div>
  );
}
