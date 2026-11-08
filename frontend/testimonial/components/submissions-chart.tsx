"use client";

import { useMemo } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { DailyCount } from "@/lib/analytics";

/**
 * The chart of submissions per day (PRD 4.6).
 *
 * A client component, because the chart library draws into the page and
 * measures the space it is given.
 *
 * The days with no submissions are in the series as zeros, so the line dips
 * to nothing on a quiet day rather than skipping it -- a skipped day would
 * draw a straight line across a day that never happened.
 */
export function SubmissionsChart({ series }: { series: DailyCount[] }) {
  const data = useMemo(
    () =>
      series.map((day) => ({
        date: day.date,
        count: day.count,
        label: formatDay(day.date),
      })),
    [series],
  );

  const total = useMemo(
    () => series.reduce((sum, day) => sum + day.count, 0),
    [series],
  );

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
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: -16 }}>
          <defs>
            {/*
              The gradient's colour comes from a CSS variable rather than
              `currentColor`: a `<stop>` inside `<defs>` does not inherit
              `currentColor` from the page, so it would render black at a
              near-zero opacity and the area would look empty.
            */}
            <linearGradient id="submissionFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--chart-ink)" stopOpacity={0.25} />
              <stop offset="100%" stopColor="var(--chart-ink)" stopOpacity={0.02} />
            </linearGradient>
          </defs>

          <CartesianGrid
            strokeDasharray="3 3"
            stroke="var(--chart-grid)"
            vertical={false}
          />
          <XAxis
            dataKey="date"
            tickFormatter={(value: string) => formatDay(value)}
            tick={{ fontSize: 11, fill: "var(--chart-grid)" }}
            axisLine={false}
            tickLine={false}
            minTickGap={24}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fontSize: 11, fill: "var(--chart-grid)" }}
            axisLine={false}
            tickLine={false}
            width={40}
          />
          <Tooltip
            labelFormatter={(value) => formatDay(String(value))}
            formatter={(value) => [`${value} submission${value === 1 ? "" : "s"}`, "Submissions"]}
            contentStyle={{
              borderRadius: "0.5rem",
              border: "1px solid #e4e4e7",
              fontSize: "0.75rem",
            }}
          />
          <Area
            type="monotone"
            dataKey="count"
            stroke="var(--chart-ink)"
            strokeWidth={2}
            fill="url(#submissionFill)"
            dot={false}
            activeDot={{ r: 4 }}
            // The chart is drawn straight away rather than animated in. An
            // animated area starts as an empty plot, which reads as "no
            // submissions" while it grows.
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

/**
 * A day as `3 Jun`, or `3 Jun 25` when the year is not this one.
 *
 * The date is a UTC day, so it is read in UTC. Reading it in the
 * browser's timezone would shift it to the previous day for anybody
 * behind UTC.
 */
function formatDay(date: string): string {
  const parsed = new Date(`${date}T00:00:00Z`);

  if (Number.isNaN(parsed.getTime())) {
    return date;
  }

  return parsed.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    timeZone: "UTC",
    ...(parsed.getUTCFullYear() === new Date().getUTCFullYear()
      ? {}
      : { year: "2-digit" }),
  });
}
