import { redirect } from "next/navigation";
import type { ReactNode } from "react";

import { getCurrentUser } from "@/lib/session";

/**
 * Layout for the sign-in and account-recovery pages.
 *
 * Kept separate from the dashboard shell so the auth pages can be centred and
 * uncluttered, and so a signed-in user landing here can be redirected away.
 */
export default async function AuthLayout({ children }: { children: ReactNode }) {
  // A signed-in user has no business on a sign-in page, so they are sent
  // straight to the dashboard.
  const user = await getCurrentUser();
  if (user) {
    redirect("/dashboard");
  }

  return (
    <div className="flex min-h-full flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">{children}</div>
    </div>
  );
}
