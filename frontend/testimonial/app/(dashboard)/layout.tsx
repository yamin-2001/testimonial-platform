import { redirect } from "next/navigation";
import type { ReactNode } from "react";

import { DashboardNav } from "@/components/dashboard-nav";
import { getCurrentUser } from "@/lib/session";

/**
 * The signed-in shell.
 *
 * Route protection lives here as well as in `proxy.ts`: the proxy is a fast
 * first check that avoids rendering, but the layout is the authoritative
 * one, because a proxy can be bypassed and Next.js documents verifying
 * authorization inside the render path rather than relying on the proxy
 * alone.
 */
export default async function DashboardLayout({ children }: { children: ReactNode }) {
  const user = await getCurrentUser();

  if (!user) {
    redirect("/login");
  }

  return (
    <div className="flex min-h-full flex-1 flex-col">
      <DashboardNav user={user}>
        <main className="flex-1 px-4 py-8 sm:px-6 lg:px-8">
          <div className="mx-auto max-w-5xl">{children}</div>
        </main>
      </DashboardNav>
    </div>
  );
}
