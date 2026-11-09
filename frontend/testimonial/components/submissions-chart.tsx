"use client";

import dynamic from "next/dynamic";

import { DailyCount } from "@/lib/analytics";

/**
 * The chart of submissions per day (PRD 4.6).
 *
 * The chart itself is drawn by a charting library, which is by far the
 * largest dependency in the app. It is loaded on demand rather than up front,
 * so the dashboard's first paint does not wait for it: the empty state and the
 * chart's frame are rendered here, and the drawing is filled in when the
 * library arrives.
 *
 * The days with no submissions are in the series as zeros, so the line dips
 * to nothing on a quiet day rather than skipping it -- a skipped day would
 * draw a straight line across a day that never happened.
 */

/**
 * The drawing, loaded on demand.
 *
 * `ssr: false` because the library measures the space it is given, so there
 * is nothing to draw on the server. The frame around it is already in place,
 * so the chart simply appears once the library has loaded.
 */
const ChartPlot = dynamic(
  () => import("@/components/submissions-chart-plot").then((m) => m.ChartPlot),
  {
    ssr: false,
    loading: () => (
      <div
        className="h-64 w-full animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-900/40"
        aria-hidden="true"
      />
    ),
  },
);

export function SubmissionsChart({ series }: { series: DailyCount[] }) {
  const total = series.reduce((sum, day) => sum + day.count, 0);

  if (total === 0) {
    // A chart of nothing is a flat line along the bottom, which reads as
    // "broken" rather than "no submissions yet". Saying so plainly is
    // clearer.
    return (
      <p
        data-testid="chart-empty"
        className="rounded-xl border border-dashed border-zinc-300 px-4 py-10 text-center text-sm text-zinc-500 dark:border-zinc-700 dark:text-zinc-400"
      >
        No submissions in this period yet. Once somebody sends one it will
        show up here.
      </p>
    );
  }

  return (
    <div
      data-testid="submissions-chart"
      className="h-64 w-full"
      role="img"
      aria-label={`Submissions per day. ${total} in total over ${series.length} days.`}
    >
      <ChartPlot series={series} />
    </div>
  );
}
