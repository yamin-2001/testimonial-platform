"use client";

import { useEffect, useRef, useState } from "react";

import { ChartRangePicker } from "@/components/chart-range-picker";
import { RecentSubmissions } from "@/components/recent-submissions";
import { SubmissionsChart } from "@/components/submissions-chart";
import { ApiError } from "@/lib/api";
import {
  DashboardAnalytics,
  DEFAULT_RANGE_DAYS,
  getDashboardAnalytics,
} from "@/lib/analytics";

/**
 * The dashboard's analytics (PRD 4.6).
 *
 * The chart's range is chosen in the browser, so the numbers are fetched from
 * the browser too -- the server-rendered page could only ever show the
 * default range.
 *
 * <p>Each fetch takes a number, and only the newest one may write to the
 * state: choosing a range while a request is in flight would otherwise let the
 * older, wider answer land last and undo the choice.
 */
export function AnalyticsPanel({
  initial,
}: {
  initial: DashboardAnalytics | null;
}) {
  const [analytics, setAnalytics] = useState<DashboardAnalytics | null>(initial);
  const [days, setDays] = useState<number>(
    initial?.range ?? DEFAULT_RANGE_DAYS,
  );
  const [error, setError] = useState<string | null>(null);

  const latestRequest = useRef(0);

  useEffect(() => {
    // The first render already has the default range's data, so there is
    // nothing to fetch until the range changes.
    if (analytics?.range === days) {
      return;
    }

    const request = ++latestRequest.current;

    getDashboardAnalytics(days)
      .then((next) => {
        if (request !== latestRequest.current) {
          return;
        }
        setAnalytics(next);
        setError(null);
      })
      .catch((caught) => {
        if (request !== latestRequest.current) {
          return;
        }
        setError(
          caught instanceof ApiError
            ? caught.message
            : "The numbers could not be loaded. Please try again.",
        );
      });
  }, [days, analytics?.range]);

  if (!analytics) {
    return (
      <p
        role="status"
        data-testid="analytics-unavailable"
        className="rounded-xl border border-zinc-200 px-4 py-3 text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400"
      >
        The numbers could not be loaded. Please try again.
      </p>
    );
  }

  // The range on screen is the one that was asked for only once the fetch
  // for it has landed; until then the previous range's data is still shown.
  const settled = analytics.range === days;

  return (
    <div className="space-y-8">
      <section aria-labelledby="chart-heading">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2
            id="chart-heading"
            className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
          >
            Submissions per day
          </h2>

          <ChartRangePicker value={days} onChange={setDays} />
        </div>

        <div className="mt-3">
          <SubmissionsChart series={analytics.series} />
        </div>

        <p
          className="mt-2 text-xs text-zinc-500 dark:text-zinc-400"
          data-testid="chart-range-summary"
        >
          {settled ? `${analytics.range} days` : "Loading…"}
        </p>
      </section>

      <section aria-labelledby="recent-heading">
        <h2
          id="recent-heading"
          className="text-base font-semibold text-zinc-900 dark:text-zinc-100"
        >
          Recent submissions
        </h2>

        <RecentSubmissions recent={analytics.recent} />
      </section>

      {error ? (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {error}
        </p>
      ) : null}
    </div>
  );
}
