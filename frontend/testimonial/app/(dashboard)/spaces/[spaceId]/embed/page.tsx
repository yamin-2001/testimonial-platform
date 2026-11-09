import { headers } from "next/headers";
import { notFound } from "next/navigation";

import { EmbedConfigurator } from "@/components/embed-configurator";
import { EmbedConfiguration, EmbedContent } from "@/lib/embeds";
import { apiFetchWithSession } from "@/lib/session";
import { DashboardSummary, Space } from "@/lib/spaces";

/**
 * A Space's embed configuration (PRD 4.8).
 *
 * The owner chooses how the widget looks, sees a preview that updates on
 * every change, and copies the snippet to paste into their own site.
 *
 * The live content comes from the public embed endpoint, which is what the
 * snippet will fetch. Reading it here means the preview shows the real
 * testimonials rather than a picture of them.
 */
export default async function EmbedConfigPage({
  params,
}: {
  params: Promise<{ spaceId: string }>;
}) {
  const { spaceId } = await params;

  // The origin is resolved before the fan-out, so the requests below do not
  // each have to wait for it.
  const origin = await scriptOrigin();

  const [space, configuration, summary] = await Promise.all([
    apiFetchWithSession<Space>(`/api/spaces/${spaceId}`),
    apiFetchWithSession<EmbedConfiguration>(`/api/spaces/${spaceId}/embed`),
    apiFetchWithSession<DashboardSummary>("/api/spaces/dashboard/summary"),
  ]);

  if (!space || !configuration) {
    notFound();
  }

  // The public content is read once the Space is known, because it needs the
  // Space's slug. The Space is not fetched again here.
  const content = await fetchPublicContent(space, origin);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Embed “{space.title}”
        </h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Put your testimonials on any website. The embed shows the ones on the
          Wall of Love, and nothing else.
        </p>
      </div>

      {space.readOnly ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          <p className="font-medium">This Space is read-only.</p>
          <p className="mt-1">
            You are on the Free plan and own more Spaces than the limit allows.
            The oldest Spaces stay editable; upgrade to Premium to make this
            one editable again. Your existing embed keeps working.
          </p>
        </div>
      ) : null}

      <EmbedConfigurator
        spaceId={space.id}
        slug={space.slug}
        scriptOrigin={origin}
        configuration={configuration}
        premium={summary?.premium ?? false}
        initialContent={content}
      />
    </div>
  );
}

/**
 * The live content the embed shows.
 *
 * The Space is passed in rather than fetched again: the page has already read
 * it, and reading it twice would ask the backend for the same thing twice.
 *
 * A Space with no testimonials on the Wall of Love yet has no content to
 * show, which is not an error.
 */
async function fetchPublicContent(
  space: Space,
  origin: string,
): Promise<EmbedContent | null> {
  try {
    const response = await fetch(
      `${origin}/api/public/embed/${encodeURIComponent(space.slug)}`,
    );
    if (!response.ok) {
      return null;
    }
    return (await response.json()) as EmbedContent;
  } catch {
    return null;
  }
}

/**
 * The origin the embed script and the data endpoint are served from.
 *
 * The snippet has to point at the site that serves the script, not at
 * whatever origin the dashboard happens to be on, because the snippet is
 * pasted into somebody else's page.
 */
async function scriptOrigin(): Promise<string> {
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
