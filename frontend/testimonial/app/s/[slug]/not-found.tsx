import Link from "next/link";

/**
 * The page a visitor sees when the Space's URL does not exist.
 *
 * Scoped to `/s/[slug]`, so a public page that has been deleted says
 * something useful rather than the application's generic not-found page.
 */
export default function SpaceNotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-12">
      <div className="w-full max-w-md text-center">
        <h1 className="text-3xl font-semibold">Page not found</h1>
        <p className="mt-2 text-base text-neutral-600">
          This page could not be found. The link may be wrong, or the page may
          have been taken down.
        </p>
        <Link
          href="/"
          className="mt-6 inline-block rounded-lg border border-neutral-300 px-4 py-2 text-sm font-medium hover:bg-neutral-50"
        >
          Go to the homepage
        </Link>
      </div>
    </main>
  );
}
