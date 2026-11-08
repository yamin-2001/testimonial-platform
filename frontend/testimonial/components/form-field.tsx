import type { InputHTMLAttributes } from "react";

/**
 * A labelled text input with an error message.
 *
 * The error is wired up with `aria-describedby` and `aria-invalid` so that
 * screen readers announce it, which matters for the field-level errors the
 * backend returns (PRD 4.5).
 */
interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  name: string;
  error?: string;
}

export function FormField({
  label,
  name,
  error,
  id,
  ...inputProps
}: FormFieldProps) {
  const inputId = id ?? name;
  const errorId = `${inputId}-error`;

  return (
    <div className="space-y-1.5">
      <label
        htmlFor={inputId}
        className="block text-sm font-medium text-zinc-700 dark:text-zinc-300"
      >
        {label}
      </label>
      <input
        id={inputId}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`block w-full rounded-lg border px-3 py-2 text-sm shadow-sm outline-none transition placeholder:text-zinc-400 focus:ring-2 focus:ring-zinc-900 focus:ring-offset-1 disabled:opacity-60 dark:bg-zinc-900 dark:text-zinc-100 ${
          error
            ? "border-red-500 focus:ring-red-500"
            : "border-zinc-300 dark:border-zinc-700"
        }`}
        {...inputProps}
      />
      {error ? (
        <p id={errorId} className="text-sm text-red-600 dark:text-red-400">
          {error}
        </p>
      ) : null}
    </div>
  );
}
