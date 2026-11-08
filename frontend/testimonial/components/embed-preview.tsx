"use client";

import { EmbedContent, EmbedSettings } from "@/lib/embeds";

/**
 * The live preview of an embed (PRD 4.8).
 *
 * It renders the same shape the embed script renders, so what the owner sees
 * here is what a visitor sees on the host site. The styles are inline rather
 * than in a stylesheet, because the preview lives inside the dashboard and
 * cannot rely on the embed script's scoped stylesheet having been loaded.
 *
 * The class names match the ones the embed script uses, so the two cannot
 * drift: the script's CSS is written against these names.
 */
export function EmbedPreview({
  content,
  settings,
}: {
  content: EmbedContent | null;
  settings: EmbedSettings;
}) {
  const dark = settings.mode === "dark";
  const carousel = settings.layout === "carousel";

  const background = dark ? "#18181b" : "#ffffff";
  const surface = dark ? "#27272a" : "#fafafa";
  const text = dark ? "#fafafa" : "#18181b";
  const muted = dark ? "#a1a1aa" : "#71717a";
  const border = dark ? "#3f3f46" : "#e4e4e7";

  // The background colour is only used when it is a plain hex colour, so a
  // hand-edited value cannot put arbitrary CSS into the preview either.
  const custom = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(
    settings.backgroundColor.trim(),
  )
    ? settings.backgroundColor.trim()
    : null;

  const testimonials = content?.testimonials ?? [];

  return (
    <div
      className={`tw-embed tw-embed-${dark ? "dark" : "light"} rounded-xl border p-4`}
      data-testid="embed-preview"
      style={{
        backgroundColor: custom ?? background,
        color: text,
        fontFamily:
          'ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      }}
    >
      {content?.title ? (
        <h3
          className="tw-embed-heading text-base font-bold"
          style={{ color: text, margin: "0 0 4px" }}
        >
          {content.title}
        </h3>
      ) : null}

      {content?.subtitle ? (
        <p className="tw-embed-subtitle" style={{ color: muted, margin: "0 0 16px" }}>
          {content.subtitle}
        </p>
      ) : null}

      {testimonials.length === 0 ? (
        <p className="tw-embed-empty" style={{ color: muted, fontSize: 13 }}>
          No testimonials yet. Add some to the Wall of Love and they will show
          up here.
        </p>
      ) : (
        <div
          className={carousel ? "tw-embed-carousel" : "tw-embed-grid"}
          style={
            carousel
              ? { display: "flex", gap: 16, overflowX: "auto" }
              : {
                  display: "grid",
                  gap: 16,
                  gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
                }
          }
        >
          {testimonials.map((testimonial) => (
            <article
              key={testimonial.id}
              className="tw-embed-card"
              style={{
                backgroundColor: surface,
                border: `1px solid ${border}`,
                borderRadius: 12,
                padding: 16,
                flex: carousel ? "0 0 240px" : undefined,
              }}
            >
              {testimonial.rating ? (
                <div
                  className="tw-embed-stars"
                  role="img"
                  aria-label={`${testimonial.rating} stars`}
                  style={{ color: "#f59e0b", marginBottom: 6, letterSpacing: 1 }}
                >
                  {"★".repeat(Math.min(5, testimonial.rating))}
                </div>
              ) : null}

              <p
                className="tw-embed-card-name"
                style={{ color: text, margin: 0, fontSize: 14, fontWeight: 600 }}
              >
                {testimonial.name}
              </p>

              {testimonial.companyName || testimonial.jobTitle ? (
                <p
                  className="tw-embed-card-meta"
                  style={{ color: muted, margin: "2px 0 0", fontSize: 12 }}
                >
                  {[testimonial.companyName, testimonial.jobTitle]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              ) : null}

              <p
                className="tw-embed-card-message"
                style={{
                  color: text,
                  margin: "10px 0 0",
                  fontSize: 14,
                  lineHeight: 1.5,
                  whiteSpace: "pre-wrap",
                  overflowWrap: "anywhere",
                }}
              >
                {testimonial.message}
              </p>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
