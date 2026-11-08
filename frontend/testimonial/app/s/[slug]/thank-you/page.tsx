import Link from "next/link";
import { notFound } from "next/navigation";

import { fetchPublicSpace } from "@/lib/public-spaces";
import { randomThankYouGif } from "@/lib/thank-you-gifs";
import { themeCss } from "@/lib/themes";

/**
 * The page a visitor sees after sending a testimonial (PRD 4.5).
 *
 * Its own route rather than a state swap inside the form, so that a refresh
 * does not lose it and it can be linked to.
 *
 * The animation is chosen here, on the server, so it does not change when
 * the page re-renders.
 */
export default async function ThankYouPage({
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
      className="flex min-h-screen flex-col items-center justify-center px-4 py-12"
    >
      <div className="w-full max-w-md text-center">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={randomThankYouGif()}
          alt=""
          width={160}
          height={120}
          className="mx-auto rounded-xl"
          data-testid="thank-you-gif"
        />

        <h1 style={css.heading} className="mt-6 text-3xl">
          Thank you!
        </h1>
        <p style={css.muted} className="mt-2 text-base">
          Your testimonial has been sent to {space.title}. We appreciate you
          taking the time.
        </p>

        <Link
          href={`/s/${space.slug}`}
          style={css.button}
          className="mt-6 inline-block rounded-lg px-4 py-2 text-sm font-medium"
        >
          Send another
        </Link>
      </div>
    </main>
  );
}
