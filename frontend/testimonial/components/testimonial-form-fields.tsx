import { themeCss } from "@/lib/themes";
import { FieldSettings, OPTIONAL_FIELDS } from "@/lib/spaces";

/**
 * The fields of the public form, in the order they appear.
 *
 * Name, email and the testimonial text are always collected and cannot be
 * turned off (PRD 4.3). The optional fields follow, each shown only when
 * the Space collects it.
 */
export interface FormFieldDefinition {
  /** The name of the field in the submitted form. */
  name: string;
  /** The label shown above the input. */
  label: string;
  /** The kind of input. */
  kind: "text" | "email" | "url" | "textarea" | "social";
  /** Whether the field must be filled in. */
  required: boolean;
  /** The placeholder, when the field wants one. */
  placeholder?: string;
}

/**
 * The always-present fields (PRD 4.3).
 */
export const FIXED_FIELDS: FormFieldDefinition[] = [
  {
    name: "name",
    label: "Your name",
    kind: "text",
    required: true,
    placeholder: "Ada Lovelace",
  },
  {
    name: "email",
    label: "Your email",
    kind: "email",
    required: true,
    placeholder: "you@example.com",
  },
];

/**
 * The testimonial text field.
 *
 * Always present and always required (PRD 4.3). It is separate from the
 * fixed fields because it is a textarea and comes last.
 */
export const MESSAGE_FIELD: FormFieldDefinition = {
  name: "message",
  label: "Your testimonial",
  kind: "textarea",
  required: true,
  placeholder: "What do you like about us?",
};

/**
 * The optional fields, in the order they are shown.
 */
const OPTIONAL_FIELD_DEFINITIONS: Record<string, FormFieldDefinition> = {
  companyName: {
    name: "companyName",
    label: "Company",
    kind: "text",
    required: false,
    placeholder: "Acme Inc.",
  },
  jobTitle: {
    name: "jobTitle",
    label: "Job title",
    kind: "text",
    required: false,
    placeholder: "Head of Engineering",
  },
  websiteUrl: {
    name: "websiteUrl",
    label: "Website",
    kind: "url",
    required: false,
    placeholder: "https://example.com",
  },
  socialLinks: {
    name: "socialLinks",
    label: "Social media links",
    kind: "social",
    required: false,
    placeholder: "https://linkedin.com/in/you",
  },
};

/**
 * The fields a Space's form actually collects.
 *
 * A field that is turned off is not in the list at all, which is what the
 * public form shows and what the backend rejects a submission for.
 *
 * A field the configuration does not mention is on, matching the backend's
 * rule: the default is to collect everything optional, so a Space saved
 * before a field existed still collects it.
 */
export function visibleFields(
  formConfig: Record<string, FieldSettings> | undefined,
): FormFieldDefinition[] {
  const fields = [...FIXED_FIELDS];

  for (const field of OPTIONAL_FIELDS) {
    const settings = formConfig?.[field.key];
    if (settings && !settings.enabled) {
      continue;
    }
    const definition = OPTIONAL_FIELD_DEFINITIONS[field.key];
    fields.push({ ...definition, required: settings?.required ?? false });
  }

  fields.push(MESSAGE_FIELD);
  return fields;
}

/**
 * The public form, rendered.
 *
 * This is the form a customer fills in. It is used by the preview in the
 * Space wizard and, from the next step, by the public page itself, so that
 * what the owner sees in the preview is what the customer gets.
 *
 * The `preview` flag makes the form inert. Inside the wizard the preview
 * sits within the wizard's own `<form>`, so a live form there would be
 * validated along with it: the browser would refuse to submit the wizard
 * while the preview's empty required fields sat inside it, and the wizard
 * would never advance.
 */
export function TestimonialFormFields({
  themeKey,
  formConfig,
  ratingEnabled,
  title,
  subtitle,
  preview = false,
}: {
  themeKey: string;
  formConfig?: Record<string, FieldSettings>;
  ratingEnabled: boolean;
  title: string;
  subtitle?: string | null;
  /** Whether this is an inert picture of the form rather than the form. */
  preview?: boolean;
}) {
  const css = themeCss(themeKey);
  const fields = visibleFields(formConfig);

  return (
    <div
      style={css.page}
      className="rounded-xl border p-6"
      data-testid="testimonial-form"
    >
      <h3 style={css.heading} className="text-xl">
        {title}
      </h3>
      {subtitle ? (
        <p style={css.muted} className="mt-1 text-sm">
          {subtitle}
        </p>
      ) : null}

      <div className="mt-5 space-y-4">
        {fields.map((field) => (
          <Field key={field.name} field={field} css={css} preview={preview} />
        ))}

        {ratingEnabled ? (
          <div>
            <span
              style={css.muted}
              className="block text-sm font-medium"
            >
              Rating
            </span>
            <div className="mt-1.5 flex gap-1">
              {[1, 2, 3, 4, 5].map((star) => (
                <span
                  key={star}
                  role="img"
                  aria-label={`${star} star${star === 1 ? "" : "s"}`}
                  className="text-xl leading-none"
                  style={{ color: css.accent }}
                >
                  ★
                </span>
              ))}
            </div>
          </div>
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
            className="mt-0.5 h-4 w-4 rounded"
            readOnly={preview}
          />
          <span style={css.muted} className="text-sm">
            You can publish my testimonial on this site.
          </span>
        </label>
      </div>
    </div>
  );
}

/**
 * One field of the form.
 */
function Field({
  field,
  css,
  preview,
}: {
  field: FormFieldDefinition;
  css: ReturnType<typeof themeCss>;
  preview: boolean;
}) {
  const label = (
    <span style={css.muted} className="block text-sm font-medium">
      {field.label}
      {field.required ? <span aria-hidden="true"> *</span> : null}
    </span>
  );

  if (field.kind === "textarea") {
    return (
      <label className="block">
        {label}
        <textarea
          name={field.name}
          rows={4}
          required={!preview && field.required}
          readOnly={preview}
          tabIndex={preview ? -1 : undefined}
          placeholder={field.placeholder}
          style={css.input}
          className="mt-1.5 block w-full rounded-lg border px-3 py-2 text-sm outline-none"
        />
      </label>
    );
  }

  return (
    <label className="block">
      {label}
      <input
        type={field.kind === "social" ? "url" : field.kind}
        name={field.name}
        required={!preview && field.required}
        readOnly={preview}
        tabIndex={preview ? -1 : undefined}
        placeholder={field.placeholder}
        style={css.input}
        className="mt-1.5 block w-full rounded-lg border px-3 py-2 text-sm outline-none"
      />
    </label>
  );
}
