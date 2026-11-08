import Link from "next/link";

import { apiFetchWithSession } from "@/lib/session";
import { DashboardSummary, Space } from "@/lib/spaces";

/**
 * The dashboard (PRD 4.6).
 *
 * The numbers come from the backend, which counts only the signed-in user's
 * own data. The chart and the recent-submissions list arrive with the
 * analytics work; this page shows the totals the backend already has.
 */
export default async function DashboardPage() {
  const [summary, spaces] = await Promise.all([
    apiFetchWithSession<DashboardSummary>("/api/spaces/dashboard/summary"),
    apiFetchWithSession<Space[]>("/api/spaces"),
  ]);

  const spaceList = spaces ?? [];

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            Dashboard
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Everything you have collected, in one place.
          </p>
        </div>

        <Link
          href="/spaces/new"
          className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          New Space
        </Link>
      </div>

      {!summary?.premium ? (
        <div className="rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm dark:border-zinc-800 dark:bg-zinc-900/40">
          <p className="font-medium text-zinc-900 dark:text-zinc-100">
            You&apos;re on the Free plan
          </p>
          <p className="mt-1 text-zinc-600 dark:text-zinc-400">
            You can own {summary?.spaceLimit ?? 3} Spaces and collect{" "}
            {summary?.totalTestimonials ?? 0 === 0 ? "100" : "100"} testimonials
            in each. Premium unlocks more Spaces, premium themes and the
            carousel embed.
          </p>
        </div>
      ) : null}

      <dl className="grid gap-4 sm:grid-cols-3">
        <Stat label="Spaces" value={summary?.totalSpaces ?? 0} />
        <Stat label="Testimonials" value={summary?.totalTestimonials ?? 0} />
        <Stat label="Unique submitters" value={summary?.uniqueSubmitters ?? 0} />
      </dl>

      <section aria-labelledby="spaces-heading">
        <div className="flex items-center justify-between">
          <h2
            id="spaces-heading"
            className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
          >
            Your Spaces
          </h2>
          {spaceList.length > 0 ? (
            <Link
              href="/spaces"
              className="text-sm font-medium text-zinc-600 underline underline-offset-4 hover:no-underline dark:text-zinc-400"
            >
              View all
            </Link>
          ) : null}
        </div>

        {spaceList.length === 0 ? (
          <div className="mt-3 rounded-xl border border-dashed border-zinc-300 p-10 text-center dark:border-zinc-700">
            <h3 className="text-base font-medium text-zinc-900 dark:text-zinc-100">
              No Spaces yet
            </h3>
            <p className="mx-auto mt-1 max-w-sm text-sm text-zinc-500 dark:text-zinc-400">
              A Space is a page you share with your customers to collect
              testimonials. Creating one is the next step.
            </p>
          </div>
        ) : (
          <ul className="mt-3 space-y-2">
            {spaceList.slice(0, 5).map((space) => (
              <li key={space.id}>
                <Link
                  href={`/spaces/${space.id}`}
                  className="flex items-center justify-between gap-4 rounded-xl border border-zinc-200 px-4 py-3 transition hover:border-zinc-300 hover:bg-zinc-50 dark:border-zinc-800 dark:hover:border-zinc-700 dark:hover:bg-zinc-900/40"
                >
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-medium text-zinc-900 dark:text-zinc-100">
                      {space.title}
                    </span>
                    <span className="block truncate text-xs text-zinc-500 dark:text-zinc-400">
                      /s/{space.slug}
                    </span>
                  </span>
                  <span className="shrink-0 text-sm text-zinc-500 dark:text-zinc-400">
                    {space.testimonialCount}{" "}
                    {space.testimonialCount === 1 ? "testimonial" : "testimonials"}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

/**
 * One of the headline numbers.
 */
function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-zinc-200 px-4 py-4 dark:border-zinc-800">
      <dt className="text-sm text-zinc-500 dark:text-zinc-400">{label}</dt>
      <dd className="mt-1 text-3xl font-semibold tabular-nums text-zinc-900 dark:text-zinc-50">
        {value}
      </dd>
    </div>
  );
}
