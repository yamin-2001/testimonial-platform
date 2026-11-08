package com.yamin.testimonial.analytics;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * The dashboard's numbers (PRD 4.6).
 *
 * <p>Everything here counts only the signed-in user's own data. The queries
 * join through {@code spaces.user_id}, so another owner's testimonials are
 * not merely filtered out afterwards -- they are never selected.
 *
 * <p><strong>Days with no submissions are reported as zero.</strong> The
 * database only returns the buckets that have something in them, so the empty
 * days are filled in here. A chart that skipped them would draw a line
 * through two days that never happened, which reads as activity rather than
 * its absence.
 *
 * <p><strong>Buckets are UTC days</strong> (PRD 4.6). The database buckets
 * by UTC too, so the two agree. The day a submission belongs to is the UTC
 * day it was created on, whatever timezone the owner is in.
 */
@Service
public class AnalyticsService {

	/**
	 * The range the dashboard shows when the owner does not choose one.
	 */
	public static final int DEFAULT_RANGE_DAYS = 30;

	/**
	 * The longest range the dashboard will show.
	 *
	 * <p>A bound, so a hand-made request cannot ask for a chart with a
	 * million points in it.
	 */
	public static final int MAX_RANGE_DAYS = 365;

	/**
	 * How many recent submissions the dashboard lists.
	 */
	public static final int RECENT_LIMIT = 10;

	private final TestimonialRepository testimonials;
	private final Clock clock;

	/**
	 * The constructor Spring uses.
	 *
	 * <p>There are two constructors, so this one is marked: without it
	 * Spring cannot tell which to use.
	 */
	@Autowired
	public AnalyticsService(TestimonialRepository testimonials) {
		this(testimonials, Clock.systemUTC());
	}

	/**
	 * The constructor the tests use, with a clock they control.
	 */
	public AnalyticsService(TestimonialRepository testimonials, Clock clock) {
		this.testimonials = testimonials;
		this.clock = clock;
	}

	/**
	 * The daily submission counts for a range of days ending today.
	 *
	 * @param ownerId the signed-in user
	 * @param days how many days to include, ending today
	 * @return one entry per day, oldest first, including the empty ones
	 */
	@Transactional(readOnly = true)
	public List<DailyCount> dailyCounts(UUID ownerId, int days) {
		int range = clampRange(days);

		// The range ends at the start of tomorrow, so a submission made
		// today is included and one made tomorrow is not.
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		LocalDate firstDay = today.minusDays(range - 1L);

		Instant from = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		Map<LocalDate, Long> counts = new LinkedHashMap<>();
		for (Object[] row : testimonials.countSubmissionsPerDayBetween(ownerId, from, to)) {
			LocalDate day = toLocalDate(row[0]);
			if (day != null) {
				counts.put(day, ((Number) row[1]).longValue());
			}
		}

		List<DailyCount> series = new ArrayList<>(range);
		for (int i = 0; i < range; i++) {
			LocalDate day = firstDay.plusDays(i);
			series.add(new DailyCount(day, counts.getOrDefault(day, 0L)));
		}

		return series;
	}

	/**
	 * The most recent submissions across the user's Spaces.
	 */
	@Transactional(readOnly = true)
	public List<RecentSubmission> recentSubmissions(UUID ownerId) {
		return testimonials.findRecentByOwnerId(ownerId, PageRequest.of(0, RECENT_LIMIT))
				.stream()
				.map(view -> new RecentSubmission(
						view.id(),
						view.name(),
						view.message(),
						view.rating(),
						view.spaceId(),
						view.spaceTitle(),
						view.spaceSlug(),
						view.submittedAt()))
				.toList();
	}

	/**
	 * The range to use, within the allowed bounds.
	 */
	private int clampRange(int days) {
		if (days < 1) {
			return DEFAULT_RANGE_DAYS;
		}
		return Math.min(days, MAX_RANGE_DAYS);
	}

	/**
	 * The UTC day a bucket belongs to.
	 *
	 * <p>The database buckets by {@code date_trunc('day', created_at at time
	 * zone 'UTC')}, which PostgreSQL returns as a timestamp without a zone.
	 * The driver hands it over as a {@link LocalDateTime}, and because the
	 * bucketing already moved it into UTC, the date part of it is the UTC
	 * day. It is read defensively rather than cast, so a driver that chooses
	 * another type does not break the chart.
	 */
	private static LocalDate toLocalDate(Object bucket) {
		if (bucket instanceof LocalDate date) {
			return date;
		}
		if (bucket instanceof LocalDateTime dateTime) {
			return dateTime.toLocalDate();
		}
		if (bucket instanceof java.sql.Date date) {
			return date.toLocalDate();
		}
		if (bucket instanceof java.sql.Timestamp timestamp) {
			return timestamp.toLocalDateTime().toLocalDate();
		}
		if (bucket instanceof Instant instant) {
			return instant.atZone(ZoneOffset.UTC).toLocalDate();
		}
		if (bucket instanceof java.time.OffsetDateTime offset) {
			return offset.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
		}
		return null;
	}

	/**
	 * One day's submission count.
	 *
	 * @param day the UTC day
	 * @param count how many testimonials were submitted that day
	 */
	public record DailyCount(LocalDate day, long count) {
	}

	/**
	 * One of the owner's most recent submissions.
	 *
	 * @param id the testimonial's id
	 * @param name the submitter's name
	 * @param message the testimonial text
	 * @param rating the rating, if one was given
	 * @param spaceId the Space's id
	 * @param spaceTitle the Space it was submitted to
	 * @param spaceSlug that Space's slug
	 * @param submittedAt when it was submitted
	 */
	public record RecentSubmission(
			UUID id,
			String name,
			String message,
			Short rating,
			UUID spaceId,
			String spaceTitle,
			String spaceSlug,
			Instant submittedAt) {
	}
}
