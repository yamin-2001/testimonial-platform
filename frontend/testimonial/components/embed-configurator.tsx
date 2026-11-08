"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import { EmbedPreview } from "@/components/embed-preview";
import { ApiError } from "@/lib/api";
import {
  EmbedConfiguration,
  EmbedContent,
  EmbedLayout,
  EmbedMode,
  PUBLIC_PROFILE_FIELDS,
  PublicProfileFieldKey,
  UpdateEmbedInput,
  updateEmbedConfiguration,
} from "@/lib/embeds";

/**
 * The embed configuration page (PRD 4.8).
 *
 * The owner chooses a layout, a mode, a background colour and whether the
 * cards animate, and picks which submitter fields the embed publishes. The
 * preview updates on every change, and the snippet is regenerated to match.
 *
 * The premium options are shown locked for a Free user, with an upgrade
 * prompt. The backend refuses them too, so the lock is not the only thing
 * standing between a Free user and a carousel (PRD 6.12).
 */
export function EmbedConfigurator({
  spaceId,
  slug,
  scriptOrigin,
  configuration,
  premium,
  initialContent,
}: {
  spaceId: string;
  slug: string;
  /** The origin the embed script and the data endpoint are served from. */
  scriptOrigin: string;
  configuration: EmbedConfiguration;
  premium: boolean;
  initialContent: EmbedContent | null;
}) {
  const [settings, setSettings] = useState(configuration.settings);
  const [published, setPublished] = useState<PublicProfileFieldKey[]>(
    configuration.publicProfileFields
      .filter((field) => field.enabled)
      .map((field) => field.key),
  );
  const [content, setContent] = useState<EmbedContent | null>(initialContent);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  /**
   * The preview's content, with the published fields filtered to what the
   * owner has just chosen.
   *
   * The preview updates on every change, so it has to react to the field
   * choices without waiting for a save. The filtering is done here rather
   * than in the backend, because the backend only publishes what is saved.
   */
  const previewContent = useMemo<EmbedContent | null>(() => {
    if (!content) {
      return null;
    }

    return {
      ...content,
      testimonials: content.testimonials.map((testimonial) => ({
        ...testimonial,
        companyName: published.includes("companyName")
          ? testimonial.companyName
          : null,
        jobTitle: published.includes("jobTitle") ? testimonial.jobTitle : null,
        websiteUrl: published.includes("websiteUrl")
          ? testimonial.websiteUrl
          : null,
        socialLinks: published.includes("socialLinks")
          ? testimonial.socialLinks
          : [],
      })),
    };
  }, [content, published]);

  const snippet = useMemo(
    () => embedSnippet(scriptOrigin, slug),
    [scriptOrigin, slug],
  );

  const setValue = useCallback(
    <K extends keyof typeof settings>(key: K, value: (typeof settings)[K]) => {
      setSettings((current) => ({ ...current, [key]: value }));
      setSaved(false);
      setErrors((current) => {
        if (!(key in current)) {
          return current;
        }
        const rest = { ...current };
        delete rest[key];
        return rest;
      });
    },
    [],
  );

  /**
   * Reloads the published content, so the preview shows the testimonials
   * that are on the Wall of Love right now.
   *
   * The state is set from the promise's callbacks rather than synchronously,
   * so the effect that starts the load does not itself change state.
   */
  const reload = useCallback(() => {
    return fetch(`${scriptOrigin}/api/public/embed/${encodeURIComponent(slug)}`)
      .then((response) => (response.ok ? response.json() : null))
      .then((body) => {
        if (body) {
          setContent(body as EmbedContent);
        }
      })
      .catch(() => {
        // The preview is a convenience; a failure leaves the last content.
      });
  }, [scriptOrigin, slug]);

  useEffect(() => {
    reload();
  }, [reload]);

  async function onSave() {
    setErrors({});
    setMessage(null);
    setSaving(true);

    const payload: UpdateEmbedInput = {
      layout: settings.layout,
      mode: settings.mode,
      animation: settings.animation,
      publicProfileFields: published,
    };

    // The background colour is only sent when it is set, because sending an
    // empty one would switch it off -- and a Free user may not set one in the
    // first place.
    if (settings.backgroundColor.trim()) {
      payload.backgroundColor = settings.backgroundColor.trim();
    }

    try {
      const savedConfiguration = await updateEmbedConfiguration(spaceId, payload);
      setSettings(savedConfiguration.settings);
      setSaved(true);
      await reload();
    } catch (caught) {
      if (caught instanceof ApiError) {
        setErrors(caught.fieldErrors);
        setMessage(caught.message);
      } else {
        setMessage("Something went wrong. Please try again.");
      }
    }
    setSaving(false);
  }

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <div className="space-y-8">
        <section aria-labelledby="layout-heading" className="space-y-3">
          <h2
            id="layout-heading"
            className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
          >
            Layout
          </h2>

          <div className="grid gap-3 sm:grid-cols-2">
            <LayoutChoice
              label="Masonry"
              description="A grid of cards."
              selected={settings.layout === "masonry"}
              onSelect={() => setValue("layout", "masonry" as EmbedLayout)}
            />
            <LayoutChoice
              label="Carousel"
              description="A row of cards that slides."
              selected={settings.layout === "carousel"}
              locked={!premium}
              onSelect={() => setValue("layout", "carousel" as EmbedLayout)}
            />
          </div>
        </section>

        <section aria-labelledby="mode-heading" className="space-y-3">
          <h2
            id="mode-heading"
            className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
          >
            Light or dark
          </h2>

          <div className="grid gap-3 sm:grid-cols-2">
            <LayoutChoice
              label="Light"
              description="For a light page."
              selected={settings.mode === "light"}
              onSelect={() => setValue("mode", "light" as EmbedMode)}
            />
            <LayoutChoice
              label="Dark"
              description="For a dark page."
              selected={settings.mode === "dark"}
              onSelect={() => setValue("mode", "dark" as EmbedMode)}
            />
          </div>
        </section>

        <section aria-labelledby="background-heading" className="space-y-3">
          <h2
            id="background-heading"
            className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
          >
            Background colour
          </h2>

          {premium ? (
            <>
              <label
                htmlFor="embed-background"
                className="block text-sm text-zinc-700 dark:text-zinc-300"
              >
                A hex colour, like{" "}
                <code className="font-mono">#1f2937</code>
              </label>
              <input
                id="embed-background"
                type="text"
                value={settings.backgroundColor}
                onChange={(event) => setValue("backgroundColor", event.target.value)}
                placeholder="#1f2937"
                aria-invalid={errors.backgroundColor ? true : undefined}
                aria-describedby={errors.backgroundColor ? "embed-background-error" : undefined}
                className="block w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
              />
            </>
          ) : (
            <Locked
              message="A background colour is part of the Premium plan."
              error={errors.backgroundColor}
            />
          )}

          {errors.backgroundColor ? (
            <p
              id="embed-background-error"
              className="text-sm text-red-600 dark:text-red-400"
            >
              {errors.backgroundColor}
            </p>
          ) : null}
        </section>

        <section aria-labelledby="animation-heading" className="space-y-3">
          <h2
            id="animation-heading"
            className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
          >
            Animation
          </h2>

          {premium ? (
            <label className="flex items-center gap-3 text-sm text-zinc-700 dark:text-zinc-300">
              <input
                type="checkbox"
                data-testid="embed-animation"
                checked={settings.animation}
                onChange={(event) => setValue("animation", event.target.checked)}
                className="h-4 w-4 rounded border-zinc-300 text-zinc-900 focus:ring-zinc-900 dark:border-zinc-600 dark:bg-zinc-900"
              />
              Fade the cards in as they appear
            </label>
          ) : (
            <Locked
              message="Animation is part of the Premium plan."
              error={errors.animation}
            />
          )}
        </section>

        <section aria-labelledby="fields-heading" className="space-y-3">
          <h2
            id="fields-heading"
            className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
          >
            What the embed shows
          </h2>

          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            The name, the testimonial and the rating are always shown. The
            submitter&apos;s email never is.
          </p>

          <ul className="divide-y divide-zinc-200 rounded-xl border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
            {PUBLIC_PROFILE_FIELDS.map((field) => {
              const checked = published.includes(field.key);
              return (
                <li key={field.key} className="px-4 py-3">
                  <label className="flex items-center justify-between gap-4">
                    <span className="text-sm text-zinc-900 dark:text-zinc-100">
                      {field.label}
                    </span>
                    <span className="flex items-center gap-2 text-sm text-zinc-600 dark:text-zinc-400">
                      {checked ? "Shown" : "Hidden"}
                      <input
                        type="checkbox"
                        aria-label={`Show ${field.label}`}
                        data-testid={`embed-field-${field.key}`}
                        checked={checked}
                        onChange={(event) => {
                          setPublished((current) =>
                            event.target.checked
                              ? [...current, field.key]
                              : current.filter((key) => key !== field.key),
                          );
                          setSaved(false);
                        }}
                        className="h-4 w-4 rounded border-zinc-300 text-zinc-900 focus:ring-zinc-900 dark:border-zinc-600 dark:bg-zinc-900"
                      />
                    </span>
                  </label>
                </li>
              );
            })}
          </ul>
        </section>

        {message ? (
          <p
            role="alert"
            className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-300"
          >
            {message}
          </p>
        ) : null}

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onSave}
            disabled={saving}
            className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-zinc-700 disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            {saving ? "Saving…" : "Save embed"}
          </button>

          {saved ? (
            <span
              className="text-sm text-emerald-700 dark:text-emerald-400"
              data-testid="embed-saved"
            >
              Saved.
            </span>
          ) : null}
        </div>
      </div>

      <div className="space-y-6">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
            Preview
          </p>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            This updates as you change the options.
          </p>

          <div className="mt-3">
            <EmbedPreview content={previewContent} settings={settings} />
          </div>
        </div>

        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
            Snippet
          </p>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Paste this into any page. It works without this app.
          </p>

          <SnippetBox snippet={snippet} />
        </div>
      </div>
    </div>
  );
}

/**
 * One layout or mode choice.
 */
function LayoutChoice({
  label,
  description,
  selected,
  locked,
  onSelect,
}: {
  label: string;
  description: string;
  selected: boolean;
  locked?: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      disabled={locked}
      data-testid={`embed-choice-${label.toLowerCase()}`}
      className={`rounded-xl border p-4 text-left transition ${
        selected
          ? "border-zinc-900 ring-1 ring-zinc-900 dark:border-zinc-50 dark:ring-zinc-50"
          : "border-zinc-200 hover:border-zinc-300 dark:border-zinc-800 dark:hover:border-zinc-700"
      } ${locked ? "cursor-not-allowed opacity-60" : ""}`}
    >
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
          {label}
        </span>
        {locked ? (
          <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-300">
            Premium
          </span>
        ) : null}
      </div>
      <span className="mt-1 block text-xs text-zinc-500 dark:text-zinc-400">
        {locked ? `Upgrade to Premium to use the ${label.toLowerCase()}.` : description}
      </span>
    </button>
  );
}

/**
 * A locked option, with the reason and an upgrade prompt.
 */
function Locked({ message, error }: { message: string; error?: string }) {
  return (
    <div
      className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 dark:border-amber-900 dark:bg-amber-950/40"
      data-testid="embed-locked"
    >
      <p className="text-sm font-medium text-amber-900 dark:text-amber-200">
        {error ?? message}
      </p>
      <p className="mt-1 text-sm text-amber-800 dark:text-amber-300">
        Upgrade to Premium to unlock this option.
      </p>
    </div>
  );
}

/**
 * The generated snippet, with a copy button.
 */
function SnippetBox({ snippet }: { snippet: string }) {
  const [state, setState] = useState<"idle" | "copied" | "failed">("idle");

  async function onCopy() {
    try {
      await navigator.clipboard.writeText(snippet);
      setState("copied");
    } catch {
      // The browser refused the clipboard. Selecting the text lets the user
      // copy it by hand instead.
      setState("failed");
    }

    setTimeout(() => setState("idle"), 2500);
  }

  return (
    <div className="mt-3">
      <textarea
        readOnly
        value={snippet}
        data-testid="embed-snippet"
        onFocus={(event) => event.currentTarget.select()}
        rows={5}
        aria-label="Embed snippet"
        className="block w-full rounded-lg border border-zinc-300 bg-zinc-50 px-3 py-2 font-mono text-xs text-zinc-900 outline-none dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
      />

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={onCopy}
          className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
        >
          {state === "copied"
            ? "Copied!"
            : state === "failed"
              ? "Press Ctrl+C to copy"
              : "Copy snippet"}
        </button>

        {state === "failed" ? (
          <span className="text-sm text-zinc-500 dark:text-zinc-400">
            Your browser blocked the clipboard. Select the text to copy it.
          </span>
        ) : null}
      </div>
    </div>
  );
}

/**
 * The snippet the owner pastes into their site.
 *
 * It is one script tag and one placeholder. The script is self-contained: it
 * fetches the content itself and renders the widget, so the host page needs
 * nothing else (PRD 4.8).
 */
export function embedSnippet(scriptOrigin: string, slug: string): string {
  return `<div data-testimonial-space="${slug}"></div>
<script src="${scriptOrigin}/embed.js" async></script>`;
}
