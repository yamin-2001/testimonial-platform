"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { CurrentUser, logout } from "@/lib/auth";

/**
 * The dashboard navigation bar.
 *
 * The sidebar navigation itself arrives with the Spaces work; this is the
 * header that is needed for authentication to be usable.
 */
export function DashboardNav({ user }: { user: CurrentUser }) {
  const router = useRouter();
  const [signingOut, setSigningOut] = useState(false);

  async function onSignOut() {
    setSigningOut(true);
    try {
      await logout();
    } finally {
      router.push("/login");
      router.refresh();
    }
  }

  return (
    <header className="border-b border-zinc-200 dark:border-zinc-800">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3 sm:px-6 lg:px-8">
        <Link href="/dashboard" className="font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Testimonial
        </Link>

        <div className="flex items-center gap-3">
          {!user.emailVerified ? (
            <span className="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-300">
              Email unverified
            </span>
          ) : null}

          <span className="hidden text-sm text-zinc-600 dark:text-zinc-400 sm:inline">
            {user.name || user.email}
          </span>

          <button
            type="button"
            onClick={onSignOut}
            disabled={signingOut}
            className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 disabled:opacity-60 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            {signingOut ? "Signing out…" : "Sign out"}
          </button>
        </div>
      </div>
    </header>
  );
}
