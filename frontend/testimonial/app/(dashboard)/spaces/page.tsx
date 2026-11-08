import Link from "next/link";

import { apiFetchWithSession } from "@/lib/session";
import { Space } from "@/lib/spaces";

/**
 * The Spaces list (PRD 4.2).
 *
 * Only the signed-in user's own Spaces are shown; the backend answers with
 * somebody else's as a 404.
 */
export default async function SpacesPage() {
  const spaces = await apiFetchWithSession<Space[]>("/api/spaces");
  const spaceList = spaces ?? [];

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            Spaces
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Each Space is a page you share to collect testimonials.
          </p>
        </div>

        <Link
          href="/spaces/new"
          className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          New Space
        </Link>
      </div>

      {spaceList.length === 0 ? (
        <div className="rounded-xl border border-dashed border-zinc-300 p-10 text-center dark:border-zinc-700">
          <h2 className="text-base font-medium text-zinc-900 dark:text-zinc-100">
            No Spaces yet
          </h2>
          <p className="mx-auto mt-1 max-w-sm text-sm text-zinc-500 dark:text-zinc-400">
            Create your first Space to get a public page you can share with
            your customers.
          </p>
          <Link
            href="/spaces/new"
            className="mt-4 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            Create a Space
          </Link>
        </div>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {spaceList.map((space) => (
            <li key={space.id}>
              <SpaceCard space={space} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * One Space in the list.
 */
function SpaceCard({ space }: { space: Space }) {
  return (
    <Link
      href={`/spaces/${space.id}`}
      className="block rounded-xl border border-zinc-200 p-4 transition hover:border-zinc-300 hover:bg-zinc-50 dark:border-zinc-800 dark:hover:border-zinc-700 dark:hover:bg-zinc-900/40"
    >
      <div className="flex items-start justify-between gap-3">
        <h2 className="min-w-0 truncate text-base font-medium text-zinc-900 dark:text-zinc-100">
          {space.title}
        </h2>
        {space.readOnly ? (
          <span className="shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-300">
            Read-only
          </span>
        ) : null}
      </div>

      {space.subtitle ? (
        <p className="mt-1 line-clamp-2 text-sm text-zinc-500 dark:text-zinc-400">
          {space.subtitle}
        </p>
      ) : null}

      <p className="mt-3 truncate font-mono text-xs text-zinc-500 dark:text-zinc-400">
        /s/{space.slug}
      </p>

      <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
        {space.testimonialCount}{" "}
        {space.testimonialCount === 1 ? "testimonial" : "testimonials"}
      </p>
    </Link>
  );
}
