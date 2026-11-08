import { notFound } from "next/navigation";

import { SpaceWizard } from "@/components/space-wizard";
import { apiFetchWithSession } from "@/lib/session";
import { DashboardSummary, Space, Theme } from "@/lib/spaces";

/**
 * Editing a Space (PRD 4.2, 4.3, 4.4).
 *
 * The same wizard is used for creating and editing: the form configuration
 * and the theme are configurable both before and after creation (PRD 4.3).
 */
export default async function EditSpacePage({
  params,
}: {
  params: Promise<{ spaceId: string }>;
}) {
  const { spaceId } = await params;

  const [space, themes, summary] = await Promise.all([
    apiFetchWithSession<Space>(`/api/spaces/${spaceId}`),
    apiFetchWithSession<{ themes: Theme[] }>("/api/spaces/themes"),
    apiFetchWithSession<DashboardSummary>("/api/spaces/dashboard/summary"),
  ]);

  if (!space) {
    notFound();
  }

  if (space.readOnly) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          This Space is read-only
        </h1>

        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          <p className="font-medium">
            You can look at this Space, but not change it.
          </p>
          <p className="mt-1">
            You are on the Free plan and own more Spaces than the limit allows.
            The oldest Spaces stay editable; upgrade to Premium to make this one
            editable again.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Edit “{space.title}”
        </h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Changing the form does not alter the testimonials you already have.
        </p>
      </div>

      <SpaceWizard
        themes={themes?.themes ?? []}
        space={space}
        premium={summary?.premium ?? false}
      />
    </div>
  );
}
