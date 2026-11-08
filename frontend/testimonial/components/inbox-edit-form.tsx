"use client";

import { useState } from "react";

import { ApiError } from "@/lib/api";
import {
  Testimonial,
  updateTestimonial,
} from "@/lib/testimonials";

/**
 * The edit form for one testimonial (PRD 4.7).
 *
 * Editing changes the text and is reflected in embeds on the next load. Only
 * the fields the owner actually changes are sent, so editing the text leaves
 * the name, the email and the rating alone.
 */
export function InboxEditForm({
  spaceId,
  testimonial,
  onSaved,
  onCancel,
}: {
  spaceId: string;
  testimonial: Testimonial;
  onSaved: (updated: Testimonial) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(testimonial.name);
  const [email, setEmail] = useState(testimonial.email);
  const [message, setMessage] = useState(testimonial.message);
  const [rating, setRating] = useState<string>(
    testimonial.rating === null ? "" : String(testimonial.rating),
  );

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors({});

    try {
      const updated = await updateTestimonial(spaceId, testimonial.id, {
        name,
        email,
        message,
        rating: rating === "" ? undefined : Number(rating),
      });

      onSaved(updated);
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.fieldErrors);
        setError(caught.message);
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      data-testid={`edit-form-${testimonial.id}`}
      className="mt-3 space-y-3 rounded-lg border border-zinc-200 p-3 dark:border-zinc-800"
    >
      {error ? (
        <p role="alert" className="text-xs text-red-700 dark:text-red-300">
          {error}
        </p>
      ) : null}

      <div>
        <label
          htmlFor={`name-${testimonial.id}`}
          className="block text-xs font-medium text-zinc-700 dark:text-zinc-300"
        >
          Name
        </label>
        <input
          id={`name-${testimonial.id}`}
          name="name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={fieldErrors.name ? true : undefined}
          className="mt-1 block w-full rounded-md border border-zinc-300 px-2.5 py-1.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"
        />
        {fieldErrors.name ? (
          <p className="mt-1 text-xs text-red-700 dark:text-red-300">
            {fieldErrors.name}
          </p>
        ) : null}
      </div>

      <div>
        <label
          htmlFor={`email-${testimonial.id}`}
          className="block text-xs font-medium text-zinc-700 dark:text-zinc-300"
        >
          Email
        </label>
        <input
          id={`email-${testimonial.id}`}
          name="email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          aria-invalid={fieldErrors.email ? true : undefined}
          className="mt-1 block w-full rounded-md border border-zinc-300 px-2.5 py-1.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"
        />
        {fieldErrors.email ? (
          <p className="mt-1 text-xs text-red-700 dark:text-red-300">
            {fieldErrors.email}
          </p>
        ) : null}
      </div>

      <div>
        <label
          htmlFor={`message-${testimonial.id}`}
          className="block text-xs font-medium text-zinc-700 dark:text-zinc-300"
        >
          Testimonial
        </label>
        <textarea
          id={`message-${testimonial.id}`}
          name="message"
          rows={3}
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          aria-invalid={fieldErrors.message ? true : undefined}
          className="mt-1 block w-full rounded-md border border-zinc-300 px-2.5 py-1.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"
        />
        {fieldErrors.message ? (
          <p className="mt-1 text-xs text-red-700 dark:text-red-300">
            {fieldErrors.message}
          </p>
        ) : null}
      </div>

      <div>
        <label
          htmlFor={`rating-${testimonial.id}`}
          className="block text-xs font-medium text-zinc-700 dark:text-zinc-300"
        >
          Rating
        </label>
        <select
          id={`rating-${testimonial.id}`}
          name="rating"
          value={rating}
          onChange={(event) => setRating(event.target.value)}
          className="mt-1 block rounded-md border border-zinc-300 px-2.5 py-1.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"
        >
          <option value="">No rating</option>
          {[1, 2, 3, 4, 5].map((star) => (
            <option key={star} value={star}>
              {star} star{star === 1 ? "" : "s"}
            </option>
          ))}
        </select>
        {fieldErrors.rating ? (
          <p className="mt-1 text-xs text-red-700 dark:text-red-300">
            {fieldErrors.rating}
          </p>
        ) : null}
      </div>

      <div className="flex items-center gap-2">
        <button
          type="submit"
          disabled={saving}
          data-testid={`save-${testimonial.id}`}
          className="rounded-md bg-zinc-900 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-zinc-700 disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900"
        >
          {saving ? "Saving…" : "Save changes"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="rounded-md border border-zinc-300 px-3 py-1.5 text-xs font-medium text-zinc-700 transition hover:bg-zinc-50 disabled:opacity-60 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
