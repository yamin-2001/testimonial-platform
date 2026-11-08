"use client";

import { useState } from "react";

import { ApiError } from "@/lib/api";
import {
  Testimonial,
  deleteTestimonial,
  setFavorite,
  setHidden,
  setWallOfLove,
} from "@/lib/testimonials";

/**
 * The actions on one testimonial in the inbox (PRD 4.7).
 *
 * Favourite, Wall of Love, hide and delete. Every one of them is the owner's
 * to make and nothing happens without a click: a testimonial is visible and
 * off the Wall of Love from the moment it arrives (PRD 4.7).
 */
export function InboxRowActions({
  spaceId,
  testimonial,
  readOnly,
  onChange,
  onEdit,
  onDeleted,
}: {
  spaceId: string;
  testimonial: Testimonial;
  readOnly: boolean;
  onChange: (updated: Testimonial) => void;
  /** Opens the edit form for this row. */
  onEdit: () => void;
  /** Called after a delete succeeds, so the list can drop the row. */
  onDeleted?: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  /**
   * Runs one of the flag changes, then tells the list about the result.
   */
  async function run(action: () => Promise<Testimonial>) {
    setBusy(true);
    setError(null);

    try {
      onChange(await action());
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "Something went wrong. Please try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  if (readOnly) {
    return (
      <p className="text-xs text-zinc-500 dark:text-zinc-400">
        This Space is read-only, so its testimonials cannot be changed.
      </p>
    );
  }

  if (confirmingDelete) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/30">
        <p className="text-sm font-medium text-red-900 dark:text-red-200">
          Delete this testimonial?
        </p>
        <p className="mt-1 text-xs text-red-800 dark:text-red-300">
          It is removed for good and cannot be undone.
        </p>

        {error ? (
          <p role="alert" className="mt-2 text-xs text-red-700 dark:text-red-300">
            {error}
          </p>
        ) : null}

        <div className="mt-2 flex items-center gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                await deleteTestimonial(spaceId, testimonial.id);
                onDeleted?.();
              } catch (caught) {
                setError(
                  caught instanceof ApiError
                    ? caught.message
                    : "Something went wrong. Please try again.",
                );
                setBusy(false);
              }
            }}
            className="rounded-md bg-red-600 px-2.5 py-1 text-xs font-semibold text-white transition hover:bg-red-700 disabled:opacity-60"
          >
            {busy ? "Deleting…" : "Yes, delete it"}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => setConfirmingDelete(false)}
            className="rounded-md border border-zinc-300 px-2.5 py-1 text-xs font-medium text-zinc-700 transition hover:bg-zinc-50 disabled:opacity-60 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            Cancel
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="flex flex-wrap items-center gap-1.5">
        <ActionButton
          label={testimonial.favorite ? "Unfavourite" : "Favourite"}
          pressed={testimonial.favorite}
          busy={busy}
          onClick={() =>
            run(() => setFavorite(spaceId, testimonial.id, !testimonial.favorite))
          }
          testId={`favorite-${testimonial.id}`}
        />

        <ActionButton
          label={testimonial.wallOfLove ? "Remove from Wall of Love" : "Add to Wall of Love"}
          pressed={testimonial.wallOfLove}
          busy={busy}
          onClick={() =>
            run(() => setWallOfLove(spaceId, testimonial.id, !testimonial.wallOfLove))
          }
          testId={`wall-of-love-${testimonial.id}`}
        />

        <ActionButton
          label={testimonial.hidden ? "Show" : "Hide"}
          pressed={testimonial.hidden}
          busy={busy}
          onClick={() => run(() => setHidden(spaceId, testimonial.id, !testimonial.hidden))}
          testId={`hidden-${testimonial.id}`}
        />

        <ActionButton
          label="Edit"
          busy={busy}
          onClick={onEdit}
          testId={`edit-${testimonial.id}`}
        />

        <ActionButton
          label="Delete"
          busy={busy}
          onClick={() => setConfirmingDelete(true)}
          testId={`delete-${testimonial.id}`}
          danger
        />
      </div>

      {error ? (
        <p role="alert" className="mt-2 text-xs text-red-700 dark:text-red-300">
          {error}
        </p>
      ) : null}
    </div>
  );
}

/**
 * One small action button.
 *
 * A pressed button is styled differently so the owner can see at a glance
 * which flags are set, without reading each label.
 */
function ActionButton({
  label,
  onClick,
  pressed,
  busy,
  danger,
  testId,
}: {
  label: string;
  onClick: () => void;
  pressed?: boolean;
  busy: boolean;
  danger?: boolean;
  testId: string;
}) {
  const base =
    "rounded-md border px-2.5 py-1 text-xs font-medium transition disabled:opacity-60";

  const style = danger
    ? pressed
      ? "border-red-300 bg-red-50 text-red-700 hover:bg-red-100 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"
      : "border-red-300 text-red-700 hover:bg-red-50 dark:border-red-900 dark:text-red-300 dark:hover:bg-red-950/40"
    : pressed
      ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-50 dark:bg-zinc-50 dark:text-zinc-900"
      : "border-zinc-300 text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800";

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={busy}
      aria-pressed={pressed}
      data-testid={testId}
      className={`${base} ${style}`}
    >
      {label}
    </button>
  );
}
