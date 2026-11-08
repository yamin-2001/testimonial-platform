import { apiFetch } from "./api";

/**
 * The dashboard's analytics (PRD 4.6).
 *
 * Mirrors `AnalyticsDtos.AnalyticsResponse` on the Java side.
 */
export interface DashboardAnalytics {
  /** How many days the series covers. */
  range: number;
  /** One entry per day, oldest first, including the days with nothing in them. */
  series: DailyCount[];
  /** The most recent submissions, newest first. */
  recent: RecentSubmission[];
}

/**
 * One day's submission count.
 */
export interface DailyCount {
  /** The UTC day, as `YYYY-MM-DD`. */
  date: string;
  /** How many testimonials were submitted that day. */
  count: number;
}

/**
 * One of the owner's most recent submissions.
 */
export interface RecentSubmission {
  id: string;
  name: string;
  message: string;
  rating: number | null;
  spaceId: string;
  spaceTitle: string;
  spaceSlug: string;
  submittedAt: string;
}

/**
 * The ranges the chart offers.
 *
 * The PRD asks for a selectable range and names thirty days as the default
 * (PRD 4.6). Seven is there because a week is the other range people
 * actually ask for.
 */
export const CHART_RANGES = [
  { days: 7, label: "7 days" },
  { days: 30, label: "30 days" },
  { days: 90, label: "90 days" },
] as const;

/**
 * The range the chart shows when the owner does not choose one.
 */
export const DEFAULT_RANGE_DAYS = 30;

/**
 * Reads the dashboard's analytics.
 */
export function getDashboardAnalytics(days: number): Promise<DashboardAnalytics> {
  return apiFetch<DashboardAnalytics>(`/api/analytics/dashboard?days=${days}`);
}
