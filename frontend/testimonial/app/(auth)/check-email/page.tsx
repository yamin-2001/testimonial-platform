import Link from "next/link";

/**
 * Shown after registration.
 *
 * The account exists but is not verified yet, so the user is asked to check
 * their inbox before continuing (PRD 4.1).
 */
export default function CheckEmailPage() {
  return (
    <div className="space-y-6 text-center">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Check your email
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          We&apos;ve sent you a verification link. Open it to confirm your email
          address and finish setting up your account.
        </p>
      </div>

      <div className="rounded-lg bg-zinc-50 px-4 py-3 text-left text-sm text-zinc-600 dark:bg-zinc-900 dark:text-zinc-400">
        <p className="font-medium text-zinc-800 dark:text-zinc-200">
          Nothing arrived?
        </p>
        <p className="mt-1">
          Check your spam folder, or{" "}
          <Link
            href="/login"
            className="underline underline-offset-4 hover:no-underline"
          >
            sign in
          </Link>{" "}
          with the address you registered — you can still use the product while
          your address is unverified.
        </p>
      </div>

      <Link
        href="/login"
        className="inline-block text-sm font-medium text-zinc-900 underline underline-offset-4 hover:no-underline dark:text-zinc-100"
      >
        Back to sign in
      </Link>
    </div>
  );
}
