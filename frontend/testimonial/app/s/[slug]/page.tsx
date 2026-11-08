import { notFound } from "next/navigation";

import { PublicTestimonialForm } from "@/components/public-testimonial-form";
import { fetchPublicSpace } from "@/lib/public-spaces";
import { themeCss } from "@/lib/themes";

/**
 * A Space's public page (PRD 4.5).
 *
 * The page lives at the root of `app/`, outside both route groups, so it
 * inherits only the root layout and gets no sign-in redirect. A visitor fills
 * in the form without an account (PRD 6.4).
 *
 * `/s` is not one of the protected prefixes in `proxy.ts`, so no proxy change
 * is needed for it.
 */
export default async function PublicSpacePage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const space = await fetchPublicSpace(slug);

  if (!space) {
    notFound();
  }

  const css = themeCss(space.themeKey);

  return (
    <main
      style={css.page}
      className="flex min-h-screen flex-col items-center px-4 py-12"
    >
      <div className="w-full max-w-xl">
        <header className="mb-8 text-center">
          <h1 style={css.heading} className="text-3xl">
            {space.title}
          </h1>
          {space.subtitle ? (
            <p style={css.muted} className="mt-2 text-base">
              {space.subtitle}
            </p>
          ) : null}
        </header>

        {space.accepting ? (
          <PublicTestimonialForm
            slug={space.slug}
            themeKey={space.themeKey}
            formConfig={space.formConfig}
            ratingEnabled={space.ratingEnabled}
          />
        ) : (
          <div
            style={css.card}
            className="rounded-xl border p-6 text-center"
            data-testid="not-accepting"
          >
            <h2 style={css.heading} className="text-lg">
              This page is not accepting responses right now.
            </h2>
            <p style={css.muted} className="mt-2 text-sm">
              Please check back later.
            </p>
          </div>
        )}
      </div>
    </main>
  );
}
