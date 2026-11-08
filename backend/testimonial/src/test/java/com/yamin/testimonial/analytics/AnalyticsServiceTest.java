package com.yamin.testimonial.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.analytics.AnalyticsService.DailyCount;
import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * The dashboard's numbers (PRD 4.6).
 *
 * <p>The rules the PRD states are checked here against a real PostgreSQL: the
 * counts belong to the signed-in user and to nobody else, days with no
 * submissions are reported as zero, deleted testimonials are gone from the
 * counts, and the days are UTC days.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceTest extends PostgresTestContainer {

	/** The moment the clock is fixed at, so the buckets are predictable. */
	private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

	/** The UTC day the fixed clock falls on. */
	private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SpaceRepository spaceRepository;

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private AnalyticsService analyticsService;

	private AppUser owner;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		appUserRepository.deleteAll();

		// The session's timezone is pinned to UTC, so a timestamp written
		// without a zone means the same thing on every machine. The
		// application sets the same thing for its own writes.
		jdbcTemplate.execute("set time zone 'UTC'");

		owner = verifiedUser("owner@example.com");

		// The service takes its clock as a constructor argument, so the
		// tests build their own with the moment fixed.
		analyticsService = new AnalyticsService(testimonialRepository,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("the series covers the range asked for, oldest first")
	void theSeriesCoversTheRangeAskedFor() {
		List<DailyCount> series = analyticsService.dailyCounts(owner.getId(), 7);

		assertThat(series).hasSize(7);
		assertThat(series.get(0).day()).isEqualTo(TODAY.minusDays(6));
		assertThat(series.get(6).day()).isEqualTo(TODAY);
	}

	@Test
	@DisplayName("the default range is thirty days")
	void theDefaultRangeIsThirtyDays() {
		assertThat(analyticsService.dailyCounts(owner.getId(), AnalyticsService.DEFAULT_RANGE_DAYS))
				.hasSize(30);
	}

	@Test
	@DisplayName("a day with no submissions is reported as zero")
	void aDayWithNoSubmissionsIsReportedAsZero() {
		Space space = space("Acme Reviews");

		// One submission today, and one three days ago. The days between
		// them have nothing in them.
		submission(space, "Ada Lovelace", "ada@example.com", TODAY);
		submission(space, "Grace Hopper", "grace@example.com", TODAY.minusDays(3));

		List<DailyCount> series = analyticsService.dailyCounts(owner.getId(), 7);

		assertThat(series).hasSize(7);
		assertThat(countOn(series, TODAY)).isEqualTo(1);
		assertThat(countOn(series, TODAY.minusDays(3))).isEqualTo(1);

		// The empty days are there, as zero.
		assertThat(countOn(series, TODAY.minusDays(1))).isZero();
		assertThat(countOn(series, TODAY.minusDays(2))).isZero();
		assertThat(countOn(series, TODAY.minusDays(4))).isZero();
	}

	@Test
	@DisplayName("a day with several submissions counts all of them")
	void aDayWithSeveralSubmissionsCountsAllOfThem() {
		Space space = space("Acme Reviews");

		submission(space, "One", "one@example.com", TODAY);
		submission(space, "Two", "two@example.com", TODAY);
		submission(space, "Three", "three@example.com", TODAY);

		assertThat(countOn(analyticsService.dailyCounts(owner.getId(), 7), TODAY)).isEqualTo(3);
	}

	@Test
	@DisplayName("another owner's submissions are not counted")
	void anotherOwnersSubmissionsAreNotCounted() {
		AppUser other = verifiedUser("other@example.com");

		Space mine = space("Acme Reviews");
		Space theirs = spaceFor(other, "Other Reviews");

		submission(mine, "Mine", "mine@example.com", TODAY);
		submission(theirs, "Theirs", "theirs@example.com", TODAY);
		submission(theirs, "Also theirs", "also@example.com", TODAY);

		// The owner's count is their own, and nobody else's.
		assertThat(countOn(analyticsService.dailyCounts(owner.getId(), 7), TODAY)).isEqualTo(1);
		assertThat(countOn(analyticsService.dailyCounts(other.getId(), 7), TODAY)).isEqualTo(2);
	}

	@Test
	@DisplayName("the recent list shows only the owner's submissions, newest first")
	void theRecentListShowsOnlyTheOwnersSubmissions() {
		AppUser other = verifiedUser("other@example.com");

		Space mine = space("Acme Reviews");
		Space theirs = spaceFor(other, "Other Reviews");

		submission(mine, "Older", "older@example.com", TODAY.minusDays(2));
		submission(mine, "Newer", "newer@example.com", TODAY);
		submission(theirs, "Not mine", "not-mine@example.com", TODAY);

		List<AnalyticsService.RecentSubmission> recent =
				analyticsService.recentSubmissions(owner.getId());

		assertThat(recent).hasSize(2);
		assertThat(recent.get(0).name()).isEqualTo("Newer");
		assertThat(recent.get(1).name()).isEqualTo("Older");
		assertThat(recent).noneMatch(item -> item.name().equals("Not mine"));
	}

	@Test
	@DisplayName("the recent list says which Space each submission came from")
	void theRecentListSaysWhichSpaceEachSubmissionCameFrom() {
		Space space = space("Acme Reviews");
		submission(space, "Ada", "ada@example.com", TODAY);

		AnalyticsService.RecentSubmission recent =
				analyticsService.recentSubmissions(owner.getId()).get(0);

		assertThat(recent.spaceTitle()).isEqualTo("Acme Reviews");
		assertThat(recent.spaceSlug()).isEqualTo("acme-reviews");
		assertThat(recent.message()).isEqualTo("A testimonial.");
	}

	@Test
	@DisplayName("a deleted testimonial is excluded from the counts")
	void aDeletedTestimonialIsExcludedFromTheCounts() {
		Space space = space("Acme Reviews");

		submission(space, "Kept", "kept@example.com", TODAY);
		Testimonial doomed = submission(space, "Doomed", "doomed@example.com", TODAY);

		testimonialRepository.delete(doomed);
		testimonialRepository.flush();

		assertThat(countOn(analyticsService.dailyCounts(owner.getId(), 7), TODAY)).isEqualTo(1);
		assertThat(analyticsService.recentSubmissions(owner.getId()))
				.extracting(AnalyticsService.RecentSubmission::name)
				.containsExactly("Kept");
	}

	@Test
	@DisplayName("a hidden testimonial is still counted")
	void aHiddenTestimonialIsStillCounted() {
		Space space = space("Acme Reviews");

		Testimonial hidden = submission(space, "Hidden", "hidden@example.com", TODAY);
		hidden.setHidden(true);
		testimonialRepository.save(hidden);

		// Hiding takes a testimonial out of the public pages, not out of the
		// owner's own counts.
		assertThat(countOn(analyticsService.dailyCounts(owner.getId(), 7), TODAY)).isEqualTo(1);
	}

	@Test
	@DisplayName("the days are UTC days")
	void theDaysAreUtcDays() {
		Space space = space("Acme Reviews");

		// Ten minutes before midnight UTC. In a timezone behind UTC this is
		// still the same UTC day, and the bucket must say so.
		submissionAt(space, "Late", "late@example.com",
				TODAY.minusDays(1).atTime(23, 50).toInstant(ZoneOffset.UTC));

		List<DailyCount> series = analyticsService.dailyCounts(owner.getId(), 7);

		assertThat(countOn(series, TODAY.minusDays(1))).isEqualTo(1);
		assertThat(countOn(series, TODAY)).isZero();
	}

	@Test
	@DisplayName("a range longer than the maximum is trimmed to it")
	void aRangeLongerThanTheMaximumIsTrimmedToIt() {
		assertThat(analyticsService.dailyCounts(owner.getId(), 5000))
				.hasSize(AnalyticsService.MAX_RANGE_DAYS);
	}

	@Test
	@DisplayName("a range of zero falls back to the default")
	void aRangeOfZeroFallsBackToTheDefault() {
		assertThat(analyticsService.dailyCounts(owner.getId(), 0))
				.hasSize(AnalyticsService.DEFAULT_RANGE_DAYS);
	}

	@Test
	@DisplayName("a submission outside the range is not counted")
	void aSubmissionOutsideTheRangeIsNotCounted() {
		Space space = space("Acme Reviews");

		// Forty days ago, well outside the thirty-day window.
		submission(space, "Old", "old@example.com", TODAY.minusDays(40));
		submission(space, "Recent", "recent@example.com", TODAY);

		List<DailyCount> series = analyticsService.dailyCounts(owner.getId(), 30);

		assertThat(series).hasSize(30);
		assertThat(countOn(series, TODAY)).isEqualTo(1);
		assertThat(series).noneMatch(day -> day.count() > 1);
	}

	/**
	 * The count on one day of a series.
	 */
	private long countOn(List<DailyCount> series, LocalDate day) {
		return series.stream()
				.filter(entry -> entry.day().equals(day))
				.mapToLong(DailyCount::count)
				.findFirst()
				.orElse(-1L);
	}

	/**
	 * Creates a Space for the owner.
	 */
	private Space space(String title) {
		return spaceFor(owner, title);
	}

	/**
	 * Creates a Space for a user.
	 */
	private Space spaceFor(AppUser user, String title) {
		Space space = new Space();
		space.setOwner(user);
		space.setTitle(title);
		space.setSlug(title.toLowerCase().replace(' ', '-'));
		space.setThemeKey("light");
		space.setRatingEnabled(false);
		return spaceRepository.save(space);
	}

	/**
	 * Records a testimonial created on a given UTC day.
	 */
	private Testimonial submission(Space space, String name, String email, LocalDate day) {
		return submissionAt(space, name, email, day.atStartOfDay(ZoneOffset.UTC).toInstant());
	}

	/**
	 * Records a testimonial created at a given moment.
	 *
	 * <p>The creation timestamp is set by Hibernate when the row is inserted,
	 * so it is written afterwards to put the testimonial on the day the test
	 * is about.
	 *
	 * <p>The session's timezone is pinned to UTC for the write. A
	 * {@code Timestamp} without a zone is read in the session's zone, so
	 * without this the day a testimonial lands on would depend on the
	 * machine the tests run on. The application itself sets
	 * {@code hibernate.jdbc.time_zone: UTC} for the same reason.
	 */
	private Testimonial submissionAt(Space space, String name, String email, Instant createdAt) {
		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(space);
		testimonial.setName(name);
		testimonial.setEmail(email);
		testimonial.setMessage("A testimonial.");
		testimonial.setConsentGiven(true);
		Testimonial saved = testimonialRepository.saveAndFlush(testimonial);

		jdbcTemplate.update("update testimonials set created_at = ? where id = ?",
				java.sql.Timestamp.from(createdAt), saved.getId());

		return saved;
	}

	/**
	 * A verified user.
	 */
	private AppUser verifiedUser(String email) {
		AppUser user = new AppUser();
		user.setEmail(email);
		user.setName("Test User");
		user.setPasswordHash("a-hash");
		user.setEmailVerified(true);
		return appUserRepository.save(user);
	}
}
