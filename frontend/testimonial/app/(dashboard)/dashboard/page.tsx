import { getCurrentUser } from "@/lib/session";

/**
 * The dashboard.
 *
 * Authenticated users land here after signing in (PRD 4.1). The analytics and
 * Space list arrive with the dashboard work; this is the shell that
 * authentication needs.
 */
export default async function DashboardPage() {
  const user = await getCurrentUser();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Welcome{user?.name ? `, ${user.name}` : ""}
        </h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          This is where your testimonial Spaces will live.
        </p>
      </div>

      {!user?.emailVerified ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          <p className="font-medium">Your email address isn&apos;t verified yet.</p>
          <p className="mt-1">
            You can sign in and look around, but you won&apos;t be able to
            create Spaces until you confirm your address. Check your inbox for
            the verification link.
          </p>
        </div>
      ) : null}

      <div className="rounded-xl border border-dashed border-zinc-300 p-10 text-center dark:border-zinc-700">
        <h2 className="text-base font-medium text-zinc-900 dark:text-zinc-100">
          No Spaces yet
        </h2>
        <p className="mx-auto mt-1 max-w-sm text-sm text-zinc-500 dark:text-zinc-400">
          A Space is a page you share with your customers to collect
          testimonials. Creating one is the next step.
        </p>
      </div>
    </div>
  );
}
