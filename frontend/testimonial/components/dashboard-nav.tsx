"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";

import { CurrentUser, logout } from "@/lib/auth";

/**
 * One entry in the sidebar.
 */
interface NavItem {
  href: string;
  label: string;
  /** Whether the page exists yet in this build. */
  available: boolean;
}

/**
 * The sidebar entries (PRD 4.6, 4.7).
 *
 * The pages that do not exist yet are listed but not linked, so the shape of
 * the navigation is visible from the start without offering dead links.
 */
const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", available: true },
  { href: "/spaces", label: "Spaces", available: true },
  { href: "/inbox", label: "Inbox", available: true },
  { href: "/billing", label: "Billing", available: true },
  { href: "/settings", label: "Settings", available: false },
];

/**
 * The dashboard navigation: a sidebar plus a header.
 *
 * The sidebar is where the app's sections live (PRD 4.6); the header carries
 * the account state and the sign-out action.
 */
export function DashboardNav({
  user,
  children,
}: {
  user: CurrentUser;
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  return (
    <div className="flex min-h-full flex-1 flex-col lg:flex-row">
      <aside className="border-b border-zinc-200 bg-zinc-50/60 dark:border-zinc-800 dark:bg-zinc-900/40 lg:w-56 lg:shrink-0 lg:border-b-0 lg:border-r">
        <div className="flex h-full flex-col p-4">
          <Link
            href="/dashboard"
            className="px-2 text-base font-semibold tracking-tight text-zinc-900 dark:text-zinc-50"
          >
            Testimonial
          </Link>

          <nav className="mt-4 flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible">
            {NAV_ITEMS.map((item) => {
              const active =
                pathname === item.href || pathname.startsWith(`${item.href}/`);

              if (!item.available) {
                return (
                  <span
                    key={item.href}
                    aria-disabled="true"
                    className="cursor-not-allowed whitespace-nowrap rounded-lg px-3 py-2 text-sm text-zinc-400 dark:text-zinc-600"
                  >
                    {item.label}
                    <span className="ml-2 text-xs uppercase tracking-wide">
                      soon
                    </span>
                  </span>
                );
              }

              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={`whitespace-nowrap rounded-lg px-3 py-2 text-sm transition ${
                    active
                      ? "bg-zinc-900 font-medium text-white dark:bg-zinc-50 dark:text-zinc-900"
                      : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <div className="mt-auto pt-4">
            <AccountMenu user={user} />
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">{children}</div>
    </div>
  );
}

/**
 * The account state and the sign-out action.
 */
function AccountMenu({ user }: { user: CurrentUser }) {
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
    <div className="flex items-center justify-between gap-3 rounded-lg border border-zinc-200 bg-white px-3 py-2 dark:border-zinc-800 dark:bg-zinc-950">
      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-zinc-900 dark:text-zinc-100">
          {user.name || user.email}
        </p>
        {!user.emailVerified ? (
          <p className="text-xs text-amber-700 dark:text-amber-400">
            Email unverified
          </p>
        ) : null}
      </div>

      <button
        type="button"
        onClick={onSignOut}
        disabled={signingOut}
        className="shrink-0 rounded-lg border border-zinc-300 px-2.5 py-1 text-xs font-medium text-zinc-700 transition hover:bg-zinc-50 disabled:opacity-60 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
      >
        {signingOut ? "…" : "Sign out"}
      </button>
    </div>
  );
}
