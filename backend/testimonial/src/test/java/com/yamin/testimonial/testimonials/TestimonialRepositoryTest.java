package com.yamin.testimonial.testimonials;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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

		List<Testimonial> embeddable = testimonialRepository.findEmbeddableBySpaceId(space.getId());

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
				.findInboxBySpaceId(space.getId(), PageRequest.of(0, 10))
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
				.findInboxBySpaceId(space.getId(), PageRequest.of(0, 10))
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
