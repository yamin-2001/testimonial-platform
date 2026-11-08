package com.yamin.testimonial.analytics.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.yamin.testimonial.analytics.AnalyticsService;

/**
 * The shapes the dashboard API returns (PRD 4.6).
 */
public final class AnalyticsDtos {

	private AnalyticsDtos() {
	}

	/**
	 * The dashboard's analytics.
	 *
	 * @param range how many days the series covers
	 * @param series one entry per day, oldest first
	 * @param recent the most recent submissions, newest first
	 */
	public record AnalyticsResponse(
			int range,
			List<DayCountResponse> series,
			List<RecentSubmissionResponse> recent) {
	}

	/**
	 * One day's submission count.
	 *
	 * @param date the UTC day
	 * @param count how many testimonials were submitted that day
	 */
	public record DayCountResponse(LocalDate date, long count) {
	}

	/**
	 * One of the owner's most recent submissions.
	 *
	 * @param id the testimonial's id
	 * @param name the submitter's name
	 * @param message the testimonial text
	 * @param rating the rating, if one was given
	 * @param spaceTitle the Space it was submitted to
	 * @param spaceSlug that Space's slug
	 * @param submittedAt when it was submitted
	 */
	public record RecentSubmissionResponse(
			UUID id,
			String name,
			String message,
			Short rating,
			String spaceTitle,
			String spaceSlug,
			Instant submittedAt) {
	}

	/**
	 * Turns the service's own records into the API's shape.
	 */
	public static AnalyticsResponse describe(
			int range,
			List<AnalyticsService.DailyCount> series,
			List<AnalyticsService.RecentSubmission> recent) {

		return new AnalyticsResponse(
				range,
				series.stream()
						.map(day -> new DayCountResponse(day.day(), day.count()))
						.toList(),
				recent.stream()
						.map(item -> new RecentSubmissionResponse(
								item.id(),
								item.name(),
								item.message(),
								item.rating(),
								item.spaceTitle(),
								item.spaceSlug(),
								item.submittedAt()))
						.toList());
	}
}
