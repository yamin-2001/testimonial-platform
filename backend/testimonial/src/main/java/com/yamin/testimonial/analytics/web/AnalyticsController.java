package com.yamin.testimonial.analytics.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yamin.testimonial.analytics.AnalyticsService;

/**
 * The dashboard's analytics API (PRD 4.6).
 *
 * <p>One endpoint, because the dashboard shows one page and the numbers, the
 * chart and the recent submissions all belong together. Splitting them would
 * mean three round trips for one screen.
 *
 * <p>Every count is scoped to the signed-in user by the queries themselves,
 * so another owner's data is never selected, let alone returned.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	public AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	/**
	 * The dashboard's analytics.
	 *
	 * @param ownerId the signed-in user
	 * @param days how many days the chart covers, ending today. Defaults to
	 *        thirty (PRD 4.6).
	 */
	@GetMapping("/dashboard")
	public AnalyticsDtos.AnalyticsResponse dashboard(
			@AuthenticationPrincipal UUID ownerId,
			@RequestParam(name = "days", required = false) Integer days) {

		int range = days == null ? AnalyticsService.DEFAULT_RANGE_DAYS : days;

		return AnalyticsDtos.describe(
				range,
				analyticsService.dailyCounts(ownerId, range),
				analyticsService.recentSubmissions(ownerId));
	}
}
