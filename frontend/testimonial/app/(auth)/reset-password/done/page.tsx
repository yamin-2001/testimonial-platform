import Link from "next/link";

/** Shown after a password has been reset successfully. */
export default function ResetPasswordDonePage() {
  return (
    <div className="space-y-6 text-center">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Password updated
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          You can now sign in with your new password.
        </p>
      </div>
      <Link
        href="/login"
        className="inline-block w-full rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
      >
        Sign in
      </Link>
    </div>
  );
}
