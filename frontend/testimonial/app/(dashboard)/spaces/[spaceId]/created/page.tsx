import Link from "next/link";
import { headers } from "next/headers";
import { notFound } from "next/navigation";

import { CopyLinkButton } from "@/components/copy-link-button";
import { apiFetchWithSession } from "@/lib/session";
import { Space } from "@/lib/spaces";

/**
 * The success page after creating a Space (PRD 4.2).
 *
 * It shows the public URL and a button that copies it. The public form
 * itself arrives with the submission step; the URL is the path the Space will
 * be reachable at.
 */
export default async function SpaceCreatedPage({
  params,
}: {
  params: Promise<{ spaceId: string }>;
}) {
  const { spaceId } = await params;
  const space = await apiFetchWithSession<Space>(`/api/spaces/${spaceId}`);

  if (!space) {
    notFound();
  }

  const publicUrl = `${await publicOrigin()}/s/${space.slug}`;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Your Space is ready
        </h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Share this link with your customers and their testimonials will come
          back to your inbox.
        </p>
      </div>

      <div className="rounded-xl border border-zinc-200 p-6 dark:border-zinc-800">
        <label
          htmlFor="public-url"
          className="block text-xs uppercase tracking-wide text-zinc-500 dark:text-zinc-400"
        >
          Public URL
        </label>

        <CopyLinkButton url={publicUrl} />
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Link
          href={`/spaces/${space.id}`}
          className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
        >
          View the Space
        </Link>
        <Link
          href="/spaces"
          className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
        >
          All Spaces
        </Link>
      </div>
    </div>
  );
}

/**
 * The origin the public URL is shown on.
 *
 * The app knows its own origin from the request, so the link the user copies
 * is the one they can actually open. An explicit `NEXT_PUBLIC_SITE_URL` wins,
 * which is what a deployment behind a proxy sets.
 */
async function publicOrigin(): Promise<string> {
  const configured = process.env.NEXT_PUBLIC_SITE_URL;
  if (configured) {
    return configured.replace(/\/$/, "");
  }

  const headerStore = await headers();
  const host = headerStore.get("x-forwarded-host") ?? headerStore.get("host");
  if (!host) {
    return "";
  }

  const protocol = headerStore.get("x-forwarded-proto") ?? "http";
  return `${protocol}://${host}`;
}
