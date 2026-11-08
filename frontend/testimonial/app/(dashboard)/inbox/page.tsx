import { Inbox } from "@/components/inbox";
import { apiFetchWithSession } from "@/lib/session";
import { Space } from "@/lib/spaces";

/**
 * The owner's inbox (PRD 4.7).
 *
 * The Space is chosen with a query parameter, so a link to one Space's inbox
 * can be shared. Without one, the first Space is used.
 */
export default async function InboxPage({
  searchParams,
}: {
  searchParams: Promise<{ space?: string }>;
}) {
  const { space: requestedId } = await searchParams;

  const spaces = await apiFetchWithSession<Space[]>("/api/spaces");
  const spaceList = spaces ?? [];

  if (spaceList.length === 0) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Inbox
        </h1>
        <div className="rounded-xl border border-dashed border-zinc-300 p-10 text-center dark:border-zinc-700">
          <h2 className="text-base font-medium text-zinc-900 dark:text-zinc-100">
            No Spaces yet
          </h2>
          <p className="mx-auto mt-1 max-w-sm text-sm text-zinc-500 dark:text-zinc-400">
            Create a Space and share its public link. The testimonials your
            customers send will appear here.
          </p>
        </div>
      </div>
    );
  }

  // A Space that is not the user's own falls back to their first one rather
  // than erroring: the inbox is reachable from the sidebar without a Space
  // chosen, and the backend would answer with a 404 for somebody else's.
  const space =
    spaceList.find((item) => item.id === requestedId) ?? spaceList[0];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Inbox
        </h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Every testimonial sent to your Spaces, newest first.
        </p>
      </div>

      <Inbox space={space} spaces={spaceList} />
    </div>
  );
}
