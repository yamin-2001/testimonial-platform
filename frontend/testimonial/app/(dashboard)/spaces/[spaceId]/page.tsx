import Link from "next/link";
import { notFound } from "next/navigation";

import { DeleteSpaceButton } from "@/components/delete-space-button";
import { apiFetchWithSession } from "@/lib/session";
import { Space } from "@/lib/spaces";

/**
 * One Space (PRD 4.2).
 *
 * The inbox and the embed configuration for this Space arrive with their own
 * steps; this page shows what the Space is and links to the rest.
 */
export default async function SpaceDetailPage({
  params,
}: {
  params: Promise<{ spaceId: string }>;
}) {
  const { spaceId } = await params;
  const space = await apiFetchWithSession<Space>(`/api/spaces/${spaceId}`);

  if (!space) {
    notFound();
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            {space.title}
          </h1>
          {space.subtitle ? (
            <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
              {space.subtitle}
            </p>
          ) : null}
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <Link
            href={`/spaces/${space.id}/edit`}
            className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            Edit
          </Link>
          <DeleteSpaceButton space={space} />
        </div>
      </div>

      {space.readOnly ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          <p className="font-medium">This Space is read-only.</p>
          <p className="mt-1">
            You are on the Free plan and own more Spaces than the limit allows.
            The oldest Spaces stay editable; upgrade to Premium to make this
            one editable again.
          </p>
        </div>
      ) : null}

      <dl className="grid gap-4 sm:grid-cols-2">
        <Detail label="Public URL">
          <code className="font-mono text-sm">/s/{space.slug}</code>
        </Detail>
        <Detail label="Testimonials">
          <span className="text-sm tabular-nums">
            {space.testimonialCount}
          </span>
        </Detail>
        <Detail label="Theme">
          <span className="text-sm capitalize">{space.themeKey}</span>
        </Detail>
        <Detail label="Rating">
          <span className="text-sm">
            {space.ratingEnabled ? "Asked for" : "Not asked for"}
          </span>
        </Detail>
      </dl>

      <section aria-labelledby="fields-heading">
        <h2
          id="fields-heading"
          className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
        >
          Form fields
        </h2>

        <ul className="mt-3 divide-y divide-zinc-200 rounded-xl border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
          <FieldRow label="Name" note="Always collected, always required" />
          <FieldRow label="Email" note="Always collected, always required" />
          <FieldRow label="Testimonial" note="Always collected, always required" />
          <FieldRow label="Consent" note="Always shown, always optional" />
          {Object.entries(space.formConfig).map(([key, settings]) => (
            <FieldRow
              key={key}
              label={fieldLabel(key)}
              note={
                !settings.enabled
                  ? "Not collected"
                  : settings.required
                    ? "Collected, required"
                    : "Collected, optional"
              }
            />
          ))}
        </ul>
      </section>
    </div>
  );
}

/**
 * One labelled value.
 */
function Detail({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-xl border border-zinc-200 px-4 py-3 dark:border-zinc-800">
      <dt className="text-xs uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
        {label}
      </dt>
      <dd className="mt-1">{children}</dd>
    </div>
  );
}

/**
 * One row of the form-fields list.
 */
function FieldRow({ label, note }: { label: string; note: string }) {
  return (
    <li className="flex items-center justify-between gap-4 px-4 py-3">
      <span className="text-sm text-zinc-900 dark:text-zinc-100">{label}</span>
      <span className="text-xs text-zinc-500 dark:text-zinc-400">{note}</span>
    </li>
  );
}

/**
 * The display name of a configurable field.
 */
function fieldLabel(key: string): string {
  switch (key) {
    case "companyName":
      return "Company name";
    case "jobTitle":
      return "Job title";
    case "websiteUrl":
      return "Website URL";
    case "socialLinks":
      return "Social media links";
    default:
      return key;
  }
}
