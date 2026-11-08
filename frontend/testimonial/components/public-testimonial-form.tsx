"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";

import { visibleFields, FormFieldDefinition } from "@/components/testimonial-form-fields";
import { ApiError } from "@/lib/api";
import { submitTestimonial } from "@/lib/public-spaces";
import { FieldSettings } from "@/lib/spaces";
import { themeCss } from "@/lib/themes";

/**
 * The public form a visitor fills in (PRD 4.5).
 *
 * This is a separate component from the wizard's `TestimonialFormFields`
 * rather than a reuse of it, for three reasons:
 *
 * <ul>
 *   <li>the wizard's preview must stay inert -- it sits inside the wizard's
 *       own form, and a live form there would be validated along with it, so
 *       the browser would refuse to submit the wizard;</li>
 *   <li>this form is controlled, so it can show a value the visitor typed and
 *       an error the backend returned;</li>
 *   <li>the rating here is a real control the visitor uses, not the
 *       display-only stars the preview shows.</li>
 * </ul>
 *
 * The field list still comes from `visibleFields`, so the two sides cannot
 * disagree about which fields the Space collects.
 */

/**
 * The name of the hidden field that bots fill in.
 *
 * Must match `app.submission.honeypot-field` in the backend configuration.
 */
const HONEYPOT_FIELD = "companyWebsite";

/**
 * The values the form holds.
 */
type Values = Record<string, string>;

/**
 * The public form.
 */
export function PublicTestimonialForm({
  slug,
  themeKey,
  formConfig,
  ratingEnabled,
}: {
  slug: string;
  themeKey: string;
  formConfig: Record<string, FieldSettings>;
  ratingEnabled: boolean;
}) {
  const router = useRouter();
  const css = themeCss(themeKey);
  const fields = visibleFields(formConfig);

  const honeypotRef = useRef<HTMLInputElement>(null);

  const [values, setValues] = useState<Values>({});
  const [rating, setRating] = useState<number | null>(null);
  const [consent, setConsent] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const setValue = (name: string, value: string) => {
    setValues((current) => ({ ...current, [name]: value }));
  };

  const onSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrors({});
    setMessage(null);
    setSubmitting(true);

    try {
      await submitTestimonial(slug, {
        name: values.name ?? "",
        email: values.email ?? "",
        companyName: values.companyName ?? "",
        jobTitle: values.jobTitle ?? "",
        websiteUrl: values.websiteUrl ?? "",
        socialLinks: values.socialLinks
          ? values.socialLinks
              .split("\n")
              .map((link) => link.trim())
              .filter((link) => link.length > 0)
          : [],
        message: values.message ?? "",
        rating: ratingEnabled ? rating : null,
        consent,
        // The honeypot's real value, read from the field itself rather than
        // assumed to be empty. A person cannot fill it in -- it cannot be
        // seen or focused -- so anything in it came from a bot, and the
        // backend discards the submission when it finds something there.
        companyWebsite: honeypotRef.current?.value ?? "",
      });

      router.push(`/s/${slug}/thank-you`);
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors);
        setMessage(error.message);
      } else {
        setMessage("Something went wrong. Please try again.");
      }
      setSubmitting(false);
    }
  };

  return (
    <form
      onSubmit={onSubmit}
      style={css.card}
      className="rounded-xl border p-6"
      data-testid="public-form"
      noValidate
    >
      {message ? (
        <div
          role="alert"
          className="mb-4 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-800"
          data-testid="form-error"
        >
          {message}
        </div>
      ) : null}

      <div className="space-y-4">
        {fields.map((field) => (
          <PublicField
            key={field.name}
            field={field}
            css={css}
            value={values[field.name] ?? ""}
            error={errors[field.name]}
            onChange={(value) => setValue(field.name, value)}
          />
        ))}

        {ratingEnabled ? (
          <Rating
            css={css}
            rating={rating}
            error={errors.rating}
            onChange={setRating}
          />
        ) : null}

        {/*
          The consent box is always shown and always optional (PRD 4.3). Its
          value is stored with the testimonial, but it never restricts what
          is displayed.
        */}
        <label className="flex items-start gap-2.5">
          <input
            type="checkbox"
            name="consent"
            checked={consent}
            onChange={(event) => setConsent(event.target.checked)}
            className="mt-0.5 h-4 w-4 rounded"
          />
          <span style={css.muted} className="text-sm">
            You can publish my testimonial on this site.
          </span>
        </label>

        {/*
          The honeypot. It is moved off-screen rather than hidden with
          `display: none`, because a bot that can see the page can tell the
          difference -- and `display: none` elements are not submitted by
          some browsers. It is also `aria-hidden` and cannot be focused, so a
          person never sees or reaches it.
        */}
        <div
          aria-hidden="true"
          style={{
            position: "absolute",
            left: "-9999px",
            width: "1px",
            height: "1px",
            overflow: "hidden",
          }}
        >
          <label htmlFor={HONEYPOT_FIELD}>Company website</label>
          <input
            type="text"
            id={HONEYPOT_FIELD}
            name={HONEYPOT_FIELD}
            ref={honeypotRef}
            tabIndex={-1}
            autoComplete="off"
            aria-hidden="true"
          />
        </div>
      </div>

      <button
        type="submit"
        disabled={submitting}
        style={css.button}
        className="mt-6 w-full rounded-lg px-4 py-2.5 text-sm font-medium disabled:opacity-60"
      >
        {submitting ? "Sending..." : "Send testimonial"}
      </button>
    </form>
  );
}

/**
 * One field of the form.
 */
function PublicField({
  field,
  css,
  value,
  error,
  onChange,
}: {
  field: FormFieldDefinition;
  css: ReturnType<typeof themeCss>;
  value: string;
  error?: string;
  onChange: (value: string) => void;
}) {
  const errorId = `${field.name}-error`;

  const label = (
    <span style={css.muted} className="block text-sm font-medium">
      {field.label}
      {field.required ? <span aria-hidden="true"> *</span> : null}
    </span>
  );

  const describedBy = error ? errorId : undefined;
  const invalid = error ? true : undefined;

  const errorMessage = error ? (
    <p id={errorId} style={{ color: "#dc2626" }} className="text-sm">
      {error}
    </p>
  ) : null;

  if (field.kind === "textarea") {
    return (
      <div>
        <label htmlFor={field.name} className="block">
          {label}
        </label>
        <textarea
          id={field.name}
          name={field.name}
          rows={4}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          required={field.required}
          aria-invalid={invalid}
          aria-describedby={describedBy}
          placeholder={field.placeholder}
          style={css.input}
          className="mt-1.5 block w-full rounded-lg border px-3 py-2 text-sm outline-none"
        />
        {errorMessage}
      </div>
    );
  }

  return (
    <div>
      <label htmlFor={field.name} className="block">
        {label}
      </label>
      <input
        id={field.name}
        name={field.name}
        type={field.kind === "social" ? "url" : field.kind}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        required={field.required}
        aria-invalid={invalid}
        aria-describedby={describedBy}
        placeholder={field.placeholder}
        style={css.input}
        className="mt-1.5 block w-full rounded-lg border px-3 py-2 text-sm outline-none"
      />
      {errorMessage}
    </div>
  );
}

/**
 * The rating control (PRD 4.5).
 *
 * A radio group. The inputs are visually hidden but still focusable, so a
 * keyboard user can reach them and a screen reader announces them; only the
 * star glyphs are `aria-hidden`, because the radio's own label carries the
 * meaning.
 */
function Rating({
  css,
  rating,
  error,
  onChange,
}: {
  css: ReturnType<typeof themeCss>;
  rating: number | null;
  error?: string;
  onChange: (rating: number) => void;
}) {
  const errorId = "rating-error";

  return (
    <fieldset>
      <legend style={css.muted} className="block text-sm font-medium">
        Rating
      </legend>
      <div className="mt-1.5 flex gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <label key={star} className="relative cursor-pointer p-0.5">
            {/*
              The radio fills the label's box and sits under the star, so
              the star is the visible control and the radio is the thing
              that is actually clicked. A hidden-but-tiny radio would be
              covered by the glyph instead, which leaves the glyph
              swallowing the click.
            */}
            <input
              type="radio"
              name="rating"
              value={star}
              checked={rating === star}
              onChange={() => onChange(star)}
              aria-label={`${star} star${star === 1 ? "" : "s"}`}
              aria-describedby={error ? errorId : undefined}
              className="peer absolute inset-0 h-full w-full cursor-pointer opacity-0"
            />
            <span
              aria-hidden="true"
              style={{ color: css.accent }}
              className="pointer-events-none block text-2xl leading-none peer-focus-visible:outline peer-focus-visible:outline-2"
            >
              ★
            </span>
          </label>
        ))}
      </div>
      {error ? (
        <p id={errorId} style={{ color: "#dc2626" }} className="text-sm">
          {error}
        </p>
      ) : null}
    </fieldset>
  );
}
