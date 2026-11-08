"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { InboxEditForm } from "@/components/inbox-edit-form";
import { InboxRowActions } from "@/components/inbox-row-actions";
import { ApiError } from "@/lib/api";
import { Space } from "@/lib/spaces";
import {
  InboxPage,
  Testimonial,
  listInbox,
} from "@/lib/testimonials";

/**
 * How many testimonials a page holds.
 */
const PAGE_SIZE = 20;

/**
 * The owner's inbox for a Space (PRD 4.7).
 *
 * Favourites sort above non-favourites and the newest is next, which is the
 * order the backend returns. The list can be narrowed by a search over the
 * name, the email and the text, and by filters for favourites, the Wall of
 * Love, hidden testimonials and rating.
 */
export function Inbox({ space, spaces }: { space: Space; spaces: Space[] }) {
  const router = useRouter();

  const [page, setPage] = useState<InboxPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [search, setSearch] = useState("");
  const [term, setTerm] = useState("");
  const [favorite, setFavorite] = useState(false);
  const [wallOfLove, setWallOfLove] = useState(false);
  const [hidden, setHidden] = useState(false);
  const [rating, setRating] = useState<string>("");
  const [currentPage, setCurrentPage] = useState(0);

  const [editing, setEditing] = useState<string | null>(null);

  // Counts the fetches, so an older answer cannot overwrite a newer one.
  const latestRequest = useRef(0);

  /**
   * Fetches the current page of the inbox.
   *
   * The state is set from the promise's callbacks rather than synchronously,
   * so the effect that starts the fetch does not itself change state.
   *
   * <p>Each fetch takes a number, and only the newest one may write to the
   * state: changing a filter while a request is in flight would otherwise let
   * the older, wider answer land last and undo the filter.
   */
  const load = useCallback(
    () => {
      const request = ++latestRequest.current;

      return listInbox(space.id, {
        search: term || undefined,
        favorite: favorite ? true : undefined,
        wallOfLove: wallOfLove ? true : undefined,
        hidden: hidden ? true : undefined,
        rating: rating ? Number(rating) : undefined,
        page: currentPage,
        size: PAGE_SIZE,
      })
        .then((next) => {
          if (request !== latestRequest.current) {
            return;
          }
          setPage(next);
          setError(null);
          setLoading(false);
        })
        .catch((caught) => {
          if (request !== latestRequest.current) {
            return;
          }
          setError(
            caught instanceof ApiError
              ? caught.message
              : "Something went wrong. Please try again.",
          );
          setLoading(false);
        });
    },
    [space.id, term, favorite, wallOfLove, hidden, rating, currentPage],
  );

  useEffect(() => {
    load();
  }, [load]);

  /**
   * Reacts to a testimonial changing.
   *
   * The list is reloaded rather than patched in place, because the order
   * depends on the flags: a favourite sorts above the others, and the
   * backend owns that order (PRD 4.7).
   */
  function onChanged() {
    load();
  }

  /**
   * Takes a testimonial out of the list after it was deleted.
   */
  function onDeleted() {
    setEditing(null);
    // The list is reloaded so that the count and the pages stay right.
    load();
  }

  const testimonials = page?.content ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label
              htmlFor="inbox-space"
              className="block text-xs font-medium text-zinc-500 dark:text-zinc-400"
            >
              Space
            </label>
            <select
              id="inbox-space"
              value={space.id}
              onChange={(event) => {
                setCurrentPage(0);
                router.push(`/inbox?space=${event.target.value}`);
              }}
              data-testid="inbox-space"
              className="mt-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-950"
            >
              {spaces.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label
              htmlFor="inbox-search"
              className="block text-xs font-medium text-zinc-500 dark:text-zinc-400"
            >
              Search
            </label>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                setCurrentPage(0);
                setTerm(search);
              }}
              className="mt-1 flex items-center gap-2"
            >
              <input
                id="inbox-search"
                name="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Name, email or text"
                className="rounded-lg border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-950"
              />
              <button
                type="submit"
                className="rounded-lg border border-zinc-300 px-3 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
              >
                Search
              </button>
            </form>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <Filter
            label="Favourites"
            checked={favorite}
            onChange={(value) => {
              setCurrentPage(0);
              setFavorite(value);
            }}
            testId="filter-favorite"
          />
          <Filter
            label="Wall of Love"
            checked={wallOfLove}
            onChange={(value) => {
              setCurrentPage(0);
              setWallOfLove(value);
            }}
            testId="filter-wall-of-love"
          />
          <Filter
            label="Hidden"
            checked={hidden}
            onChange={(value) => {
              setCurrentPage(0);
              setHidden(value);
            }}
            testId="filter-hidden"
          />

          <div>
            <label
              htmlFor="inbox-rating"
              className="block text-xs font-medium text-zinc-500 dark:text-zinc-400"
            >
              Rating
            </label>
            <select
              id="inbox-rating"
              value={rating}
              onChange={(event) => {
                setCurrentPage(0);
                setRating(event.target.value);
              }}
              data-testid="filter-rating"
              className="mt-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-950"
            >
              <option value="">Any rating</option>
              {[1, 2, 3, 4, 5].map((star) => (
                <option key={star} value={star}>
                  {star} star{star === 1 ? "" : "s"}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {error ? (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {error}
        </p>
      ) : null}

      {loading && testimonials.length === 0 ? (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">Loading…</p>
      ) : null}

      {!loading && testimonials.length === 0 ? (
        <div className="rounded-xl border border-dashed border-zinc-300 p-10 text-center dark:border-zinc-700">
          <h2 className="text-base font-medium text-zinc-900 dark:text-zinc-100">
            No testimonials yet
          </h2>
          <p className="mx-auto mt-1 max-w-sm text-sm text-zinc-500 dark:text-zinc-400">
            Share this Space&apos;s public link and the testimonials your
            customers send will appear here.
          </p>
        </div>
      ) : null}

      {testimonials.length > 0 ? (
        <ul className="space-y-3" data-testid="inbox-list">
          {testimonials.map((testimonial) => (
            <li key={testimonial.id}>
              <InboxRow
                spaceId={space.id}
                testimonial={testimonial}
                readOnly={space.readOnly}
                editing={editing === testimonial.id}
                onEdit={() => setEditing(testimonial.id)}
                onCancelEdit={() => setEditing(null)}
                onChanged={onChanged}
                onDeleted={onDeleted}
              />
            </li>
          ))}
        </ul>
      ) : null}

      {page && page.totalPages > 1 ? (
        <nav
          aria-label="Inbox pages"
          className="flex items-center justify-between gap-3"
        >
          <button
            type="button"
            disabled={currentPage === 0}
            onClick={() => setCurrentPage(currentPage - 1)}
            className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 disabled:opacity-40 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            Previous
          </button>

          <span className="text-sm text-zinc-500 dark:text-zinc-400">
            Page {page.page + 1} of {page.totalPages} ·{" "}
            {page.totalElements}{" "}
            {page.totalElements === 1 ? "testimonial" : "testimonials"}
          </span>

          <button
            type="button"
            disabled={currentPage >= page.totalPages - 1}
            onClick={() => setCurrentPage(currentPage + 1)}
            className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 disabled:opacity-40 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            Next
          </button>
        </nav>
      ) : null}
    </div>
  );
}

/**
 * One testimonial in the inbox.
 */
function InboxRow({
  spaceId,
  testimonial,
  readOnly,
  editing,
  onEdit,
  onCancelEdit,
  onChanged,
  onDeleted,
}: {
  spaceId: string;
  testimonial: Testimonial;
  readOnly: boolean;
  editing: boolean;
  onEdit: () => void;
  onCancelEdit: () => void;
  onChanged: () => void;
  onDeleted: () => void;
}) {
  return (
    <article
      data-testid={`testimonial-${testimonial.id}`}
      className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
            {testimonial.name}
          </h3>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            {testimonial.email}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {testimonial.rating !== null ? (
            <span
              role="img"
              aria-label={`${testimonial.rating} stars`}
              className="text-sm text-amber-500"
            >
              {"★".repeat(testimonial.rating)}
            </span>
          ) : null}
          {testimonial.hidden ? (
            <span className="rounded-full bg-zinc-200 px-2 py-0.5 text-xs font-medium text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
              Hidden
            </span>
          ) : null}
          {testimonial.wallOfLove ? (
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
              Wall of Love
            </span>
          ) : null}
        </div>
      </div>

      <p className="mt-3 whitespace-pre-wrap text-sm text-zinc-700 dark:text-zinc-300">
        {testimonial.message}
      </p>

      {testimonial.companyName || testimonial.jobTitle ? (
        <p className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">
          {[testimonial.companyName, testimonial.jobTitle]
            .filter(Boolean)
            .join(" · ")}
        </p>
      ) : null}

      <p className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">
        Submitted {new Date(testimonial.createdAt).toLocaleDateString()}
      </p>

      {editing ? (
        <InboxEditForm
          spaceId={spaceId}
          testimonial={testimonial}
          onSaved={() => {
            onChanged();
            onCancelEdit();
          }}
          onCancel={onCancelEdit}
        />
      ) : (
        <div className="mt-3">
          <InboxRowActions
            spaceId={spaceId}
            testimonial={testimonial}
            readOnly={readOnly}
            onChange={onChanged}
            onEdit={onEdit}
            onDeleted={onDeleted}
          />
        </div>
      )}
    </article>
  );
}

/**
 * One filter checkbox.
 */
function Filter({
  label,
  checked,
  onChange,
  testId,
}: {
  label: string;
  checked: boolean;
  onChange: (value: boolean) => void;
  testId: string;
}) {
  return (
    <label className="flex items-center gap-2 text-sm text-zinc-700 dark:text-zinc-300">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        data-testid={testId}
        className="h-4 w-4 rounded"
      />
      {label}
    </label>
  );
}
