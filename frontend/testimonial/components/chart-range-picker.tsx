"use client";

import { CHART_RANGES } from "@/lib/analytics";

/**
 * The range the chart covers (PRD 4.6).
 *
 * A radio group rather than a `<select>`, so the options are visible at once
 * and each one is a real label: a screen reader announces the group and the
 * chosen option together.
 */
export function ChartRangePicker({
  value,
  onChange,
}: {
  value: number;
  onChange: (days: number) => void;
}) {
  return (
    <fieldset
      data-testid="chart-range-picker"
      className="flex items-center gap-1 rounded-lg border border-zinc-200 p-0.5 dark:border-zinc-800"
    >
      <legend className="sr-only">Chart range</legend>

      {CHART_RANGES.map((range) => (
        <label
          key={range.days}
          className={`cursor-pointer rounded-md px-2.5 py-1 text-xs font-medium transition ${
            value === range.days
              ? "bg-zinc-900 text-white dark:bg-zinc-50 dark:text-zinc-900"
              : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
          }`}
        >
          <input
            type="radio"
            name="chart-range"
            value={range.days}
            checked={value === range.days}
            onChange={() => onChange(range.days)}
            data-testid={`chart-range-${range.days}`}
            className="sr-only"
          />
          {range.label}
        </label>
      ))}
    </fieldset>
  );
}
