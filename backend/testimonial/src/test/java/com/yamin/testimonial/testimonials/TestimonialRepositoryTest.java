package com.yamin.testimonial.testimonials;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.support.PostgresTestContainer;

/**
 * Covers the two testimonial queries that carry the rules most easily broken:
 * what the embed shows, and how the inbox orders.
 *
 * <p>Testimonials are built through named factory methods rather than a
 * helper with positional booleans, so each call site reads unambiguously.
 */
@SpringBootTest
@ActiveProfiles("test")
class TestimonialRepositoryTest extends PostgresTestContainer {

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private SpaceRepository spaceRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	private Space space;

	@BeforeEach
	void setUp() {
		appUserRepository.deleteAll();
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();

		AppUser owner = new AppUser();
		owner.setEmail("owner-" + UUID.randomUUID() + "@example.com");
		owner.setEmailVerified(true);
		appUserRepository.save(owner);

		space = new Space();
		space.setOwner(owner);
		space.setTitle("Test Space");
		space.setSlug("space-" + UUID.randomUUID());
		space.setThemeKey("basic");
		space.setRatingEnabled(true);
		spaceRepository.save(space);
	}

	@Test
	@DisplayName("the embed shows only Wall of Love testimonials that are not hidden")
	void embedShowsOnlyWallOfLoveAndVisible() {
		testimonialRepository.save(wallOfLoveTestimonial("Ada"));
		testimonialRepository.save(hiddenWallOfLoveTestimonial("Grace"));
		testimonialRepository.save(visibleTestimonial("Alan"));

		List<Testimonial> embeddable = testimonialRepository.findEmbeddableBySpaceId(space.getId(),
				PageRequest.of(0, 10));

		// "Grace" is on the Wall of Love but hidden, so it is excluded.
		// "Alan" is visible but not on the Wall of Love, so it is excluded too.
		assertThat(embeddable).extracting(Testimonial::getName).containsExactly("Ada");
	}

	@Test
	@DisplayName("the inbox lists favorites first, then newest first")
	void inboxSortsFavoritesFirstThenNewest() {
		Testimonial older = visibleTestimonial("Older");
		setCreatedAt(older, Instant.now().minus(2, ChronoUnit.DAYS));
		Testimonial newer = visibleTestimonial("Newer");
		setCreatedAt(newer, Instant.now().minus(1, ChronoUnit.DAYS));
		Testimonial favorite = favoriteTestimonial("Favorite");
		setCreatedAt(favorite, Instant.now().minus(3, ChronoUnit.DAYS));

		testimonialRepository.saveAll(List.of(older, newer, favorite));

		List<Testimonial> inbox = testimonialRepository
				.findInbox(space.getId(), null, null, null, null, null, PageRequest.of(0, 10))
				.getContent();

		// The oldest testimonial is still first, because it is a favorite.
		assertThat(inbox).extracting(Testimonial::getName)
				.containsExactly("Favorite", "Newer", "Older");
	}

	@Test
	@DisplayName("hidden testimonials stay in the inbox, marked hidden")
	void hiddenTestimonialsRemainInInbox() {
		testimonialRepository.save(hiddenTestimonial("Hidden One"));
		testimonialRepository.save(visibleTestimonial("Shown"));

		List<Testimonial> inbox = testimonialRepository
				.findInbox(space.getId(), null, null, null, null, null, PageRequest.of(0, 10))
				.getContent();

		assertThat(inbox).hasSize(2);
		assertThat(inbox).extracting(Testimonial::getName).contains("Hidden One");
		assertThat(inbox).filteredOn(Testimonial::isHidden).hasSize(1);
	}

	@Test
	@DisplayName("unique submitters are counted case-insensitively across a user's spaces")
	void uniqueSubmittersAreCountedCaseInsensitively() {
		testimonialRepository.save(testimonialWithEmail("One", "Repeat@Example.com"));
		testimonialRepository.save(testimonialWithEmail("Two", "repeat@example.com"));
		testimonialRepository.save(testimonialWithEmail("Three", "other@example.com"));

		assertThat(testimonialRepository.countDistinctSubmittersByOwnerId(space.getOwner().getId())).isEqualTo(2);
	}

	@Test
	@DisplayName("the inbox reports an exact total, and counts it with count(*)")
	void theInboxTotalIsExactAndCountedWithCountStar() {
		for (int i = 0; i < 50; i++) {
			testimonialRepository.save(visibleTestimonial("Submitter " + i));
		}

		Page<Testimonial> page = testimonialRepository
				.findInbox(space.getId(), null, null, null, null, null, PageRequest.of(0, 10));

		// The total is exact, which is what the pager and the count in the
		// inbox header both depend on.
		assertThat(page.getTotalElements()).isEqualTo(50);
		assertThat(page.getTotalPages()).isEqualTo(5);
		assertThat(page.getContent()).hasSize(10);
	}

	@Test
	@DisplayName("the inbox count is a count(*) so that it can be answered from the index")
	void theInboxCountIsACountStar() throws NoSuchMethodException {
		// The count query is written out rather than left to Spring Data,
		// because Spring Data derives count(t.*) and count(t.*) names every
		// column of the row. The planner then has to consider that the count
		// may need any of them, so it gives up on the covering index and scans
		// the table: on a Space with a hundred thousand testimonials that is
		// three thousand eight hundred and fifty-five buffers read, against
		// ninety for count(*).
		//
		// The test is on the SQL rather than on the plan, because the plan
		// depends on how many rows there are: for a Space with fifty
		// testimonials a table scan really is the cheaper plan, and asserting
		// on it would make the test pass or fail for reasons that have
		// nothing to do with the code.
		String countQuery = countQueryOfTheInbox();

		assertThat(countQuery)
				.as("the count must be count(*) so that it can be an index-only scan")
				.contains("count(*)")
				.doesNotContain("count(t.*)");
	}

	/**
	 * The SQL Spring Data sends for the inbox's count.
	 *
	 * <p>Read out of the repository's own query annotation, so this is the
	 * statement that is actually sent rather than a copy of it that could
	 * drift away from the real one.
	 */
	private String countQueryOfTheInbox() throws NoSuchMethodException {
		Method method = TestimonialRepository.class.getMethod("findInbox", UUID.class, String.class,
				Boolean.class, Boolean.class, Boolean.class, Short.class, Pageable.class);

		Query query = method.getAnnotation(Query.class);

		assertThat(query).as("findInbox must declare an explicit count query").isNotNull();
		assertThat(query.countQuery()).as("findInbox must declare an explicit count query")
				.isNotBlank();

		return query.countQuery();
	}

	@Test
	@DisplayName("a Space reports whether it has received its first submission")
	void slugLockFollowsFirstSubmission() {
		assertThat(spaceRepository.hasAnyTestimonials(space.getId())).isFalse();

		testimonialRepository.save(visibleTestimonial("First"));

		assertThat(spaceRepository.hasAnyTestimonials(space.getId())).isTrue();
	}

	/**
	 * Overrides the generated creation timestamp.
	 *
	 * <p>{@code createdAt} is populated by Hibernate on insert and is not
	 * writable through the public API, so the field is set directly.
	 */
	private void setCreatedAt(Testimonial testimonial, Instant createdAt) {
		ReflectionTestUtils.setField(testimonial, "createdAt", createdAt);
	}

	private Testimonial visibleTestimonial(String name) {
		return baseTestimonial(name);
	}

	private Testimonial favoriteTestimonial(String name) {
		Testimonial testimonial = baseTestimonial(name);
		testimonial.setFavorite(true);
		return testimonial;
	}

	private Testimonial wallOfLoveTestimonial(String name) {
		Testimonial testimonial = baseTestimonial(name);
		testimonial.setWallOfLove(true);
		return testimonial;
	}

	private Testimonial hiddenTestimonial(String name) {
		Testimonial testimonial = baseTestimonial(name);
		testimonial.setHidden(true);
		return testimonial;
	}

	private Testimonial hiddenWallOfLoveTestimonial(String name) {
		Testimonial testimonial = wallOfLoveTestimonial(name);
		testimonial.setHidden(true);
		return testimonial;
	}

	private Testimonial testimonialWithEmail(String name, String email) {
		Testimonial testimonial = baseTestimonial(name);
		testimonial.setEmail(email);
		return testimonial;
	}

	private Testimonial baseTestimonial(String name) {
		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(space);
		testimonial.setName(name);
		testimonial.setEmail("submitter@example.com");
		testimonial.setMessage("Great product, genuinely. " + name);
		testimonial.setRating((short) 5);
		return testimonial;
	}
}
