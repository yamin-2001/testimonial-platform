"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { verifyEmail } from "@/lib/auth";

/**
 * Confirms an email address.
 *
 * The link in the email points here with the token in the query string, so
 * visiting the page is enough to confirm the address.
 */
export function VerifyEmailForm() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<"verifying" | "done" | "error">("verifying");
  // Guards against React Strict Mode running the effect twice, which would
  // consume the single-use token and report a failure.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    if (!token) return;

    verifyEmail(token)
      .then(() => setStatus("done"))
      .catch(() => setStatus("error"));
  }, [token]);

  // A link with no token never reaches the backend, so it is handled without
  // a round trip.
  if (!token) {
    return (
      <div className="space-y-6 text-center">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            Link not valid
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            This verification link is invalid or has expired.
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

  if (status === "verifying") {
    return (
      <p className="text-center text-sm text-zinc-500 dark:text-zinc-400">
        Verifying your email address…
      </p>
    );
  }

  if (status === "done") {
    return (
      <div className="space-y-6 text-center">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            Email verified
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Your address is confirmed. You can now create Spaces and collect
            testimonials.
          </p>
        </div>
        <Link
          href="/dashboard"
          className="inline-block w-full rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          Go to dashboard
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6 text-center">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Link not valid
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          This verification link is invalid or has expired.
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
