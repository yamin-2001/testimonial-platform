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
 * The drawing of the submissions chart.
 *
 * This is the only part of the dashboard that needs the charting library, so
 * it lives in its own file and is loaded on demand by `SubmissionsChart`.
 *
 * The library measures the space it is given, so it cannot be drawn on the
 * server: the frame is rendered by the shell, and this fills it in.
 */
export function ChartPlot({ series }: { series: DailyCount[] }) {
  const data = useMemo(
    () =>
      series.map((day) => ({
        date: day.date,
        count: day.count,
        label: formatDay(day.date),
      })),
    [series],
  );

  return (
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
