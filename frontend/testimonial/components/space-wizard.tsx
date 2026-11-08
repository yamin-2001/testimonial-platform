"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";

import { ApiError } from "@/lib/api";
import {
  FieldSettings,
  OPTIONAL_FIELDS,
  Space,
  Theme,
  createSpace,
  slugify,
  updateSpace,
} from "@/lib/spaces";

/**
 * The steps of the Space wizard (PRD 4.2).
 *
 * The order is the one the PRD describes: title and subtitle, then the fields
 * to collect, then the theme, then ratings.
 */
const STEPS = [
  { key: "details", label: "Details" },
  { key: "fields", label: "Fields" },
  { key: "theme", label: "Theme" },
  { key: "ratings", label: "Ratings" },
] as const;

type StepKey = (typeof STEPS)[number]["key"];

/**
 * The values the wizard collects.
 */
interface WizardValues {
  title: string;
  subtitle: string;
  slug: string;
  formConfig: Record<string, FieldSettings>;
  themeKey: string;
  ratingEnabled: boolean;
}

/**
 * The Space wizard, used for both creating and editing.
 *
 * A client component: the wizard holds its own state and posts to the backend
 * itself, so the field-level errors the backend returns can be shown next to
 * the field that caused them (PRD 4.5).
 */
export function SpaceWizard({
  themes,
  space,
  premium,
}: {
  themes: Theme[];
  space?: Space;
  premium: boolean;
}) {
  const router = useRouter();
  const editing = Boolean(space);

  const [step, setStep] = useState<StepKey>("details");
  const [values, setValues] = useState<WizardValues>({
    title: space?.title ?? "",
    subtitle: space?.subtitle ?? "",
    slug: space?.slug ?? "",
    formConfig: space?.formConfig ?? defaultFormConfig(),
    themeKey: space?.themeKey ?? themes[0]?.key ?? "light",
    ratingEnabled: space?.ratingEnabled ?? false,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const stepIndex = STEPS.findIndex((candidate) => candidate.key === step);
  const isLastStep = stepIndex === STEPS.length - 1;

  /**
   * The slug the backend would generate for the current title.
   *
   * Shown as a preview; the user can type their own, and the backend is the
   * authority on what it stores.
   */
  const suggestedSlug = useMemo(() => slugify(values.title), [values.title]);

  function setValue<K extends keyof WizardValues>(
    key: K,
    value: WizardValues[K],
  ) {
    setValues((current) => ({ ...current, [key]: value }));
    // A field-level error belongs to the value the user just changed.
    setErrors((current) => {
      if (!(key in current)) {
        return current;
      }
      const rest = { ...current };
      delete rest[key];
      return rest;
    });
  }

  function setFieldSetting(
    key: string,
    setting: keyof FieldSettings,
    value: boolean,
  ) {
    setValues((current) => {
      const field = current.formConfig[key] ?? { enabled: false, required: false };
      const next: FieldSettings = { ...field, [setting]: value };

      // A field that is off cannot be required.
      if (!next.enabled) {
        next.required = false;
      }

      return {
        ...current,
        formConfig: { ...current.formConfig, [key]: next },
      };
    });
  }

  function goToStep(target: StepKey) {
    setStep(target);
    setMessage(null);
  }

  function next() {
    if (step === "details" && !values.title.trim()) {
      setErrors({ title: "Title is required" });
      return;
    }
    setErrors({});
    const following = STEPS[stepIndex + 1];
    if (following) {
      goToStep(following.key);
    }
  }

  function back() {
    const previous = STEPS[stepIndex - 1];
    if (previous) {
      goToStep(previous.key);
    }
  }

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrors({});
    setMessage(null);

    // The one button serves both purposes: before the last step it advances,
    // on the last step it saves.
    if (!isLastStep) {
      next();
      return;
    }

    setSubmitting(true);

    try {
      const payload = {
        title: values.title.trim(),
        subtitle: values.subtitle.trim() || undefined,
        slug: values.slug.trim() || undefined,
        themeKey: values.themeKey,
        ratingEnabled: values.ratingEnabled,
        formConfig: values.formConfig,
      };

      const saved = editing
        ? await updateSpace(space!.id, payload)
        : await createSpace(payload);

      router.push(`/spaces/${saved.id}/created`);
      router.refresh();
    } catch (caught) {
      if (caught instanceof ApiError) {
        const fieldErrors = caught.fieldErrors;
        if (Object.keys(fieldErrors).length > 0) {
          setErrors(fieldErrors);
          // Show the step the offending field lives on.
          if ("title" in fieldErrors || "subtitle" in fieldErrors || "slug" in fieldErrors) {
            setStep("details");
          } else if ("themeKey" in fieldErrors) {
            setStep("theme");
          }
        } else {
          setMessage(caught.message);
        }
      } else {
        setMessage("Something went wrong. Please try again.");
      }
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-8">
      <ol className="flex flex-wrap gap-2" aria-label="Steps">
        {STEPS.map((candidate, index) => {
          const active = candidate.key === step;
          const done = index < stepIndex;
          return (
            <li key={candidate.key}>
              <button
                type="button"
                onClick={() => goToStep(candidate.key)}
                aria-current={active ? "step" : undefined}
                className={`rounded-full px-3 py-1 text-sm transition ${
                  active
                    ? "bg-zinc-900 font-medium text-white dark:bg-zinc-50 dark:text-zinc-900"
                    : done
                      ? "bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300"
                      : "text-zinc-500 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                }`}
              >
                {index + 1}. {candidate.label}
              </button>
            </li>
          );
        })}
      </ol>

      {step === "details" ? (
        <DetailsStep
          values={values}
          errors={errors}
          suggestedSlug={suggestedSlug}
          onChange={setValue}
        />
      ) : null}

      {step === "fields" ? (
        <FieldsStep values={values} onChange={setFieldSetting} />
      ) : null}

      {step === "theme" ? (
        <ThemeStep
          themes={themes}
          selected={values.themeKey}
          premium={premium}
          onChange={(key) => setValue("themeKey", key)}
        />
      ) : null}

      {step === "ratings" ? (
        <RatingsStep
          enabled={values.ratingEnabled}
          onChange={(enabled) => setValue("ratingEnabled", enabled)}
        />
      ) : null}

      {message ? (
        <p
          role="alert"
          className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-300"
        >
          {message}
        </p>
      ) : null}

      <div className="flex items-center gap-3">
        {stepIndex > 0 ? (
          <button
            type="button"
            onClick={back}
            className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            Back
          </button>
        ) : null}

        {/*
          The submit button is always rendered, but only enabled on the last
          step. Swapping it for a "Continue" button would let React reuse the
          same DOM node, and the browser would submit the form on the very
          click that advanced to the last step.
        */}
        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-zinc-700 disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {isLastStep
            ? submitting
              ? "Saving…"
              : editing
                ? "Save changes"
                : "Create Space"
            : "Continue"}
        </button>
      </div>
    </form>
  );
}

/**
 * Step 1: the title, the subtitle and the URL.
 */
function DetailsStep({
  values,
  errors,
  suggestedSlug,
  onChange,
}: {
  values: WizardValues;
  errors: Record<string, string>;
  suggestedSlug: string;
  onChange: <K extends keyof WizardValues>(
    key: K,
    value: WizardValues[K],
  ) => void;
}) {
  return (
    <section className="space-y-5">
      <div>
        <label
          htmlFor="title"
          className="block text-sm font-medium text-zinc-700 dark:text-zinc-300"
        >
          Title
        </label>
        <input
          id="title"
          name="title"
          value={values.title}
          onChange={(event) => onChange("title", event.target.value)}
          aria-invalid={errors.title ? true : undefined}
          aria-describedby={errors.title ? "title-error" : undefined}
          placeholder="Acme Reviews"
          className={`mt-1.5 block w-full rounded-lg border px-3 py-2 text-sm shadow-sm outline-none transition placeholder:text-zinc-400 focus:ring-2 focus:ring-zinc-900 focus:ring-offset-1 dark:bg-zinc-900 dark:text-zinc-100 ${
            errors.title
              ? "border-red-500 focus:ring-red-500"
              : "border-zinc-300 dark:border-zinc-700"
          }`}
        />
        {errors.title ? (
          <p id="title-error" className="mt-1.5 text-sm text-red-600 dark:text-red-400">
            {errors.title}
          </p>
        ) : null}
      </div>

      <div>
        <label
          htmlFor="subtitle"
          className="block text-sm font-medium text-zinc-700 dark:text-zinc-300"
        >
          Subtitle
        </label>
        <input
          id="subtitle"
          name="subtitle"
          value={values.subtitle}
          onChange={(event) => onChange("subtitle", event.target.value)}
          placeholder="What our customers say"
          className="mt-1.5 block w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm shadow-sm outline-none transition placeholder:text-zinc-400 focus:ring-2 focus:ring-zinc-900 focus:ring-offset-1 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
        />
        {errors.subtitle ? (
          <p className="mt-1.5 text-sm text-red-600 dark:text-red-400">
            {errors.subtitle}
          </p>
        ) : null}
      </div>

      <div>
        <label
          htmlFor="slug"
          className="block text-sm font-medium text-zinc-700 dark:text-zinc-300"
        >
          Public URL
        </label>
        <div className="mt-1.5 flex items-center gap-2">
          <span className="shrink-0 text-sm text-zinc-500 dark:text-zinc-400">
            /s/
          </span>
          <input
            id="slug"
            name="slug"
            value={values.slug}
            onChange={(event) => onChange("slug", event.target.value)}
            placeholder={suggestedSlug || "your-space"}
            aria-invalid={errors.slug ? true : undefined}
            aria-describedby={errors.slug ? "slug-error" : "slug-hint"}
            className={`block w-full rounded-lg border px-3 py-2 text-sm shadow-sm outline-none transition placeholder:text-zinc-400 focus:ring-2 focus:ring-zinc-900 focus:ring-offset-1 dark:bg-zinc-900 dark:text-zinc-100 ${
              errors.slug
                ? "border-red-500 focus:ring-red-500"
                : "border-zinc-300 dark:border-zinc-700"
            }`}
          />
        </div>
        {errors.slug ? (
          <p id="slug-error" className="mt-1.5 text-sm text-red-600 dark:text-red-400">
            {errors.slug}
          </p>
        ) : (
          <p id="slug-hint" className="mt-1.5 text-xs text-zinc-500 dark:text-zinc-400">
            Leave empty to use{" "}
            <span className="font-mono">{suggestedSlug || "your-title"}</span>. The
            URL locks once the Space receives its first testimonial.
          </p>
        )}
      </div>
    </section>
  );
}

/**
 * Step 2: which optional fields the public form collects (PRD 4.3).
 */
function FieldsStep({
  values,
  onChange,
}: {
  values: WizardValues;
  onChange: (key: string, setting: keyof FieldSettings, value: boolean) => void;
}) {
  return (
    <section className="space-y-5">
      <div>
        <h2 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
          What should the form ask for?
        </h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Name, email and the testimonial text are always collected. The
          fields below are optional.
        </p>
      </div>

      <ul className="divide-y divide-zinc-200 rounded-xl border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
        {OPTIONAL_FIELDS.map((field) => {
          const settings = values.formConfig[field.key] ?? {
            enabled: false,
            required: false,
          };
          return (
            <li key={field.key} className="px-4 py-3">
              <div className="flex items-center justify-between gap-4">
                <span className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                  {field.label}
                </span>

                <label className="flex shrink-0 items-center gap-2 text-sm text-zinc-600 dark:text-zinc-400">
                  <input
                    type="checkbox"
                    checked={settings.enabled}
                    onChange={(event) =>
                      onChange(field.key, "enabled", event.target.checked)
                    }
                    className="h-4 w-4 rounded border-zinc-300 text-zinc-900 focus:ring-zinc-900 dark:border-zinc-600 dark:bg-zinc-900"
                  />
                  Collect
                </label>
              </div>

              {settings.enabled ? (
                <label className="mt-2 flex items-center gap-2 text-sm text-zinc-600 dark:text-zinc-400">
                  <input
                    type="checkbox"
                    checked={settings.required}
                    onChange={(event) =>
                      onChange(field.key, "required", event.target.checked)
                    }
                    className="h-4 w-4 rounded border-zinc-300 text-zinc-900 focus:ring-zinc-900 dark:border-zinc-600 dark:bg-zinc-900"
                  />
                  Required
                </label>
              ) : null}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/**
 * Step 3: the theme (PRD 4.4).
 */
function ThemeStep({
  themes,
  selected,
  premium,
  onChange,
}: {
  themes: Theme[];
  selected: string;
  premium: boolean;
  onChange: (key: string) => void;
}) {
  return (
    <section className="space-y-5">
      <div>
        <h2 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
          Choose a theme
        </h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          The theme is what your public form looks like.
        </p>
      </div>

      <ul className="grid gap-3 sm:grid-cols-2">
        {themes.map((theme) => {
          const locked = theme.premium && !premium;
          const active = theme.key === selected;

          return (
            <li key={theme.key}>
              <button
                type="button"
                onClick={() => onChange(theme.key)}
                aria-pressed={active}
                disabled={locked}
                className={`w-full rounded-xl border p-4 text-left transition ${
                  active
                    ? "border-zinc-900 ring-1 ring-zinc-900 dark:border-zinc-50 dark:ring-zinc-50"
                    : "border-zinc-200 hover:border-zinc-300 dark:border-zinc-800 dark:hover:border-zinc-700"
                } ${locked ? "cursor-not-allowed opacity-60" : ""}`}
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                    {theme.label}
                  </span>
                  {locked ? (
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-300">
                      Premium
                    </span>
                  ) : null}
                </div>

                <span
                  aria-hidden="true"
                  className={`mt-3 block h-16 rounded-lg border border-zinc-200 dark:border-zinc-700 ${themePreviewClass(theme.key)}`}
                />

                {locked ? (
                  <span className="mt-2 block text-xs text-zinc-500 dark:text-zinc-400">
                    Upgrade to Premium to use this theme.
                  </span>
                ) : null}
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/**
 * Step 4: whether the public form asks for a rating (PRD 6.5).
 */
function RatingsStep({
  enabled,
  onChange,
}: {
  enabled: boolean;
  onChange: (enabled: boolean) => void;
}) {
  return (
    <section className="space-y-5">
      <div>
        <h2 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
          Ask for a rating?
        </h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          A rating is optional and can be turned off later.
        </p>
      </div>

      <label className="flex items-center gap-3 rounded-xl border border-zinc-200 px-4 py-3 dark:border-zinc-800">
        <input
          type="checkbox"
          checked={enabled}
          onChange={(event) => onChange(event.target.checked)}
          className="h-4 w-4 rounded border-zinc-300 text-zinc-900 focus:ring-zinc-900 dark:border-zinc-600 dark:bg-zinc-900"
        />
        <span className="text-sm text-zinc-900 dark:text-zinc-100">
          Show a 1&ndash;5 star rating on the public form
        </span>
      </label>
    </section>
  );
}

/**
 * The default configuration for the optional fields.
 *
 * Matches the backend's default: every optional field is collected, none is
 * required.
 */
function defaultFormConfig(): Record<string, FieldSettings> {
  const config: Record<string, FieldSettings> = {};
  for (const field of OPTIONAL_FIELDS) {
    config[field.key] = { enabled: true, required: false };
  }
  return config;
}

/**
 * A rough visual stand-in for each theme.
 *
 * The real rendering arrives with the public form; this is enough to tell the
 * themes apart in the picker (PRD 4.4).
 */
function themePreviewClass(key: string): string {
  switch (key) {
    case "dark":
      return "bg-zinc-800";
    case "midnight":
      return "bg-indigo-950";
    case "sunset":
      return "bg-orange-200";
    case "editorial":
      return "bg-amber-50";
    default:
      return "bg-zinc-50";
  }
}
