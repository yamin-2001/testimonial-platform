import Link from "next/link";

import { RecentSubmission } from "@/lib/analytics";

/**
 * The dashboard's recent-submissions list (PRD 4.6).
 *
 * The newest first, which is the order the backend returns. Each entry links
 * to the Space it came from, so the owner can go straight to it.
 */
export function RecentSubmissions({ recent }: { recent: RecentSubmission[] }) {
  if (recent.length === 0) {
    return (
      <div className="mt-3 rounded-xl border border-dashed border-zinc-300 p-10 text-center dark:border-zinc-700">
        <h3 className="text-base font-medium text-zinc-900 dark:text-zinc-100">
          No submissions yet
        </h3>
        <p className="mx-auto mt-1 max-w-sm text-sm text-zinc-500 dark:text-zinc-400">
          Share one of your Spaces&apos; public links and the testimonials your
          customers send will show up here.
        </p>
      </div>
    );
  }

  return (
    <ul className="mt-3 space-y-2" data-testid="recent-submissions">
      {recent.map((submission) => (
        <li key={submission.id}>
          <article
            data-testid={`recent-submission-${submission.id}`}
            className="rounded-xl border border-zinc-200 px-4 py-3 dark:border-zinc-800"
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                  {submission.name}
                </p>
                <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
                  <Link
                    href={`/spaces/${submission.spaceId}`}
                    className="underline underline-offset-4 hover:no-underline"
                  >
                    {submission.spaceTitle}
                  </Link>
                  {" · "}
                  <time dateTime={submission.submittedAt}>
                    {formatWhen(submission.submittedAt)}
                  </time>
                </p>
              </div>

              {submission.rating !== null ? (
                <span
                  role="img"
                  aria-label={`${submission.rating} stars`}
                  className="shrink-0 text-sm text-amber-500"
                >
                  {"★".repeat(submission.rating)}
                </span>
              ) : null}
            </div>

            <p className="mt-2 line-clamp-2 text-sm text-zinc-700 dark:text-zinc-300">
              {submission.message}
            </p>
          </article>
        </li>
      ))}
    </ul>
  );
}

/**
 * When a submission arrived, as `3 Jun` or `3 Jun 2025`.
 *
 * The timestamp is an instant, so it is shown in the reader's own timezone --
 * unlike the chart's day labels, which are UTC days and must not shift.
 */
function formatWhen(instant: string): string {
  const parsed = new Date(instant);

  if (Number.isNaN(parsed.getTime())) {
    return instant;
  }

  return parsed.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: parsed.getFullYear() === new Date().getFullYear() ? undefined : "numeric",
  });
}
