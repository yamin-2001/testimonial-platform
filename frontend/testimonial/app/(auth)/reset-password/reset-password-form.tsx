"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

import { FormField } from "@/components/form-field";
import { ApiError } from "@/lib/api";
import { resetPassword } from "@/lib/auth";

/**
 * The password reset form.
 *
 * The token comes from the emailed link. A used or expired token is rejected
 * by the backend, which is shown here as an error (PRD 4.1).
 */
export function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const token = searchParams.get("token") ?? "";

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);

    const form = new FormData(event.currentTarget);

    try {
      await resetPassword(token, String(form.get("password") ?? ""));
      router.push("/reset-password/done");
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setFieldErrors(caught.fieldErrors);
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <div className="space-y-6 text-center">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Link missing
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          This page needs a reset link. Open the link from your email, or
          request a new one.
        </p>
        <Link
          href="/forgot-password"
          className="inline-block text-sm font-medium text-zinc-900 underline underline-offset-4 hover:no-underline dark:text-zinc-100"
        >
          Request a new link
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1 text-center">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Choose a new password
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Must be at least 8 characters.
        </p>
      </div>

      <form onSubmit={onSubmit} className="space-y-4">
        <FormField
          label="New password"
          name="password"
          type="password"
          autoComplete="new-password"
          placeholder="At least 8 characters"
          error={fieldErrors.password}
          minLength={8}
          required
        />

        {error ? (
          <p
            role="alert"
            className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-300"
          >
            {error}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-zinc-700 disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {submitting ? "Saving…" : "Set new password"}
        </button>
      </form>
    </div>
  );
}
