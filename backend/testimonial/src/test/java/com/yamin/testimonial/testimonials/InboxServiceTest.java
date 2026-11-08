package com.yamin.testimonial.testimonials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.common.exception.ConflictException;
import com.yamin.testimonial.common.exception.NotFoundException;
import com.yamin.testimonial.common.exception.ValidationException;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.support.PostgresTestContainer;

/**
 * The owner's inbox for a Space's testimonials (PRD 4.7).
 *
 * <p>Runs against a real PostgreSQL, because the ordering, the filtering and
 * the pagination are all done by the database.
 */
@SpringBootTest
@ActiveProfiles("test")
class InboxServiceTest extends PostgresTestContainer {

	@Autowired
	private InboxService inboxService;

	@Autowired
	private SpaceService spaceService;

	@Autowired
	private SpaceRepository spaceRepository;

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	private AppUser owner;

	private Space space;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		appUserRepository.deleteAll();

		owner = verifiedUser("owner@example.com");
		space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false, null);
	}

	@Test
	@DisplayName("the inbox is empty when the Space has no testimonials")
	void emptyInbox() {
		Page<Testimonial> page = list();

		assertThat(page.getTotalElements()).isZero();
		assertThat(page.getContent()).isEmpty();
	}

	@Test
	@DisplayName("a favourite sorts above every non-favourite, however old it is")
	void favoritesSortFirst() {
		Testimonial oldest = testimonial("Ada", "ada@example.com", "Oldest.");
		Testimonial newest = testimonial("Grace", "grace@example.com", "Newest.");
		Testimonial middle = testimonial("Alan", "alan@example.com", "Middle.");

		// Three distinct submission times, so "newest first" is unambiguous.
		submittedAt(middle, oldest.getCreatedAt().plusSeconds(60));
		submittedAt(newest, oldest.getCreatedAt().plusSeconds(120));

		// The oldest is made a favourite after the others arrived.
		inboxService.setFavorite(owner.getId(), oldest.getId(), true);

		Page<Testimonial> page = list();

		// The favourite is first, and the other two keep their order by date.
		assertThat(page.getContent()).extracting(Testimonial::getId)
				.containsExactly(oldest.getId(), newest.getId(), middle.getId());
	}

	@Test
	@DisplayName("without a favourite the newest comes first")
	void newestFirst() {
		Testimonial first = testimonial("Ada", "ada@example.com", "First.");
		Testimonial second = testimonial("Grace", "grace@example.com", "Second.");

		// The second was submitted later, so it is the newer of the two.
		submittedAt(second, first.getCreatedAt().plusSeconds(60));

		Page<Testimonial> page = list();

		assertThat(page.getContent()).extracting(Testimonial::getId)
				.containsExactly(second.getId(), first.getId());
	}

	@Test
	@DisplayName("a favourite can be taken off again")
	void favoriteCanBeRemoved() {
		Testimonial testimonial = testimonial("Ada", "ada@example.com", "Great.");

		inboxService.setFavorite(owner.getId(), testimonial.getId(), true);
		assertThat(inboxService.get(owner.getId(), testimonial.getId()).isFavorite()).isTrue();

		inboxService.setFavorite(owner.getId(), testimonial.getId(), false);
		assertThat(inboxService.get(owner.getId(), testimonial.getId()).isFavorite()).isFalse();
	}

	@Test
	@DisplayName("a hidden testimonial stays in the inbox, marked hidden")
	void hiddenTestimonialsStayInTheInbox() {
		Testimonial testimonial = testimonial("Ada", "ada@example.com", "Great.");

		inboxService.setHidden(owner.getId(), testimonial.getId(), true);

		Page<Testimonial> page = list();
		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().get(0).isHidden()).isTrue();
	}

	@Test
	@DisplayName("the hidden filter separates hidden testimonials from visible ones")
	void hiddenFilter() {
		Testimonial visible = testimonial("Ada", "ada@example.com", "Visible.");
		Testimonial hidden = testimonial("Grace", "grace@example.com", "Hidden.");

		inboxService.setHidden(owner.getId(), hidden.getId(), true);

		assertThat(list(null, null, null, false).getContent())
				.extracting(Testimonial::getId).containsExactly(visible.getId());
		assertThat(list(null, null, null, true).getContent())
				.extracting(Testimonial::getId).containsExactly(hidden.getId());
	}

	@Test
	@DisplayName("the search finds a testimonial by name, email or text")
	void search() {
		Testimonial ada = testimonial("Ada Lovelace", "ada@example.com", "Analytical engines.");
		Testimonial grace = testimonial("Grace Hopper", "grace@example.com", "Compilers.");

		assertThat(list("ada lovelace", null, null, null).getContent())
				.extracting(Testimonial::getId).containsExactly(ada.getId());
		assertThat(list("grace@example.com", null, null, null).getContent())
				.extracting(Testimonial::getId).containsExactly(grace.getId());
		assertThat(list("compilers", null, null, null).getContent())
				.extracting(Testimonial::getId).containsExactly(grace.getId());
	}

	@Test
	@DisplayName("the search does not match on something else")
	void searchFindsNothing() {
		testimonial("Ada", "ada@example.com", "Great.");

		assertThat(list("nothing matches this", null, null, null).getTotalElements()).isZero();
	}

	@Test
	@DisplayName("the favourite filter keeps only favourites")
	void favoriteFilter() {
		Testimonial favorite = testimonial("Ada", "ada@example.com", "Great.");
		Testimonial other = testimonial("Grace", "grace@example.com", "Also great.");

		inboxService.setFavorite(owner.getId(), favorite.getId(), true);

		assertThat(list(null, true, null, null).getContent())
				.extracting(Testimonial::getId).containsExactly(favorite.getId());
		assertThat(list(null, false, null, null).getContent())
				.extracting(Testimonial::getId).containsExactly(other.getId());
	}

	@Test
	@DisplayName("the Wall of Love filter keeps only testimonials on it")
	void wallOfLoveFilter() {
		Testimonial onWall = testimonial("Ada", "ada@example.com", "Great.");
		Testimonial other = testimonial("Grace", "grace@example.com", "Also great.");

		inboxService.setWallOfLove(owner.getId(), onWall.getId(), true);

		assertThat(list(null, null, true, null).getContent())
				.extracting(Testimonial::getId).containsExactly(onWall.getId());
		assertThat(list(null, null, false, null).getContent())
				.extracting(Testimonial::getId).containsExactly(other.getId());
	}

	@Test
	@DisplayName("the rating filter keeps only testimonials with that rating")
	void ratingFilter() {
		Testimonial five = testimonial("Ada", "ada@example.com", "Great.", (short) 5);
		Testimonial three = testimonial("Grace", "grace@example.com", "Fine.", (short) 3);
		Testimonial none = testimonial("Alan", "alan@example.com", "No rating.");

		assertThat(list(null, null, null, null, (short) 5).getContent())
				.extracting(Testimonial::getId).containsExactly(five.getId());
		assertThat(list(null, null, null, null, (short) 3).getContent())
				.extracting(Testimonial::getId).containsExactly(three.getId());
		assertThat(list(null, null, null, null, (short) 1).getTotalElements()).isZero();
		assertThat(none.getId()).isNotNull();
	}

	@Test
	@DisplayName("a search and a filter are applied together")
	void searchAndFilterTogether() {
		Testimonial ada = testimonial("Ada", "ada@example.com", "Great.");
		Testimonial grace = testimonial("Grace", "grace@example.com", "Great.");

		inboxService.setFavorite(owner.getId(), ada.getId(), true);

		assertThat(list("great", true, null, null).getContent())
				.extracting(Testimonial::getId).containsExactly(ada.getId());
	}

	@Test
	@DisplayName("the inbox is paginated")
	void pagination() {
		for (int i = 0; i < 5; i++) {
			testimonial("Submitter " + i, "submitter" + i + "@example.com", "Number " + i + ".");
		}

		Page<Testimonial> first = inboxService.list(owner.getId(), space.getId(), null, null, null,
				null, null, PageRequest.of(0, 2));
		Page<Testimonial> second = inboxService.list(owner.getId(), space.getId(), null, null, null,
				null, null, PageRequest.of(1, 2));

		assertThat(first.getTotalElements()).isEqualTo(5);
		assertThat(first.getTotalPages()).isEqualTo(3);
		assertThat(first.getContent()).hasSize(2);
		assertThat(second.getContent()).hasSize(2);
		assertThat(first.getContent().get(0).getId())
				.isNotEqualTo(second.getContent().get(0).getId());
	}

	@Test
	@DisplayName("editing changes the text and leaves the rest alone")
	void editingChangesTheText() {
		Testimonial testimonial = testimonial("Ada", "ada@example.com", "The original text.");

		Testimonial edited = inboxService.edit(owner.getId(), testimonial.getId(), null, null,
				"The edited text.", null);

		assertThat(edited.getMessage()).isEqualTo("The edited text.");
		assertThat(edited.getName()).isEqualTo("Ada");
		assertThat(edited.getEmail()).isEqualTo("ada@example.com");
	}

	@Test
	@DisplayName("an edit that clears a required field is rejected")
	void editingToAnEmptyValueIsRejected() {
		Testimonial testimonial = testimonial("Ada", "ada@example.com", "Great.");

		assertThatThrownBy(() -> inboxService.edit(owner.getId(), testimonial.getId(), null, null,
				"   ", null))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("correct the highlighted fields");

		assertThat(inboxService.get(owner.getId(), testimonial.getId()).getMessage()).isEqualTo("Great.");
	}

	@Test
	@DisplayName("an edit with a bad email is rejected")
	void editingToABadEmailIsRejected() {
		Testimonial testimonial = testimonial("Ada", "ada@example.com", "Great.");

		assertThatThrownBy(() -> inboxService.edit(owner.getId(), testimonial.getId(), null,
				"not-an-email", null, null))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	@DisplayName("an edit with an out-of-range rating is rejected")
	void editingToABadRatingIsRejected() {
		Testimonial testimonial = testimonial("Ada", "ada@example.com", "Great.");

		assertThatThrownBy(() -> inboxService.edit(owner.getId(), testimonial.getId(), null, null,
				null, 9))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	@DisplayName("deleting removes the testimonial for good")
	void deletingRemovesIt() {
		Testimonial testimonial = testimonial("Ada", "ada@example.com", "Great.");

		inboxService.delete(owner.getId(), testimonial.getId());

		assertThat(testimonialRepository.findById(testimonial.getId())).isEmpty();
		assertThat(list().getTotalElements()).isZero();
	}

	@Test
	@DisplayName("one user cannot act on another user's testimonial")
	void anotherUsersTestimonialIsNotFound() {
		AppUser other = verifiedUser("other@example.com");
		Space otherSpace = spaceService.create(other.getId(), "Other Reviews", null, null, null,
				false, null);
		Testimonial theirs = testimonialIn(otherSpace, "Theirs", "theirs@example.com", "Not yours.", null);

		assertThatThrownBy(() -> inboxService.get(owner.getId(), theirs.getId()))
				.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> inboxService.setFavorite(owner.getId(), theirs.getId(), true))
				.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> inboxService.setHidden(owner.getId(), theirs.getId(), true))
				.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> inboxService.edit(owner.getId(), theirs.getId(), null, null, "Hijacked.",
				null))
				.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> inboxService.delete(owner.getId(), theirs.getId()))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	@DisplayName("one user cannot read another user's inbox")
	void anotherUsersInboxIsNotFound() {
		AppUser other = verifiedUser("other@example.com");
		Space otherSpace = spaceService.create(other.getId(), "Other Reviews", null, null, null,
				false, null);

		assertThatThrownBy(() -> inboxService.list(owner.getId(), otherSpace.getId(), null, null, null,
				null, null, PageRequest.of(0, 20)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	@DisplayName("a read-only Space is viewable but cannot be changed")
	void readOnlySpaceRejectsChanges() {
		// The Free plan allows three Spaces. The owner already owns one from
		// the setup, so two more are created and a fourth is written straight
		// to the database -- the state a downgraded user is left in: they
		// still own more Spaces than the plan allows.
		spaceService.create(owner.getId(), "Two", null, null, null, false, null);
		spaceService.create(owner.getId(), "Three", null, null, null, false, null);
		Space fourth = spaceDirectly(owner, "Four");

		Testimonial testimonial = testimonialIn(fourth, "Ada", "ada@example.com", "Great.", null);

		// The inbox is still viewable.
		assertThat(inboxService.list(owner.getId(), fourth.getId(), null, null, null, null, null,
				PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);

		// But every change is refused.
		assertThatThrownBy(() -> inboxService.setFavorite(owner.getId(), testimonial.getId(), true))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> inboxService.setWallOfLove(owner.getId(), testimonial.getId(), true))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> inboxService.setHidden(owner.getId(), testimonial.getId(), true))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> inboxService.edit(owner.getId(), testimonial.getId(), null, null,
				"Changed.", null))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> inboxService.delete(owner.getId(), testimonial.getId()))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	@DisplayName("the Wall of Love is the owner's choice, whatever the consent said")
	void wallOfLoveIgnoresConsent() {
		Testimonial withoutConsent = testimonial("Ada", "ada@example.com", "Great.");
		Testimonial withConsent = testimonial("Grace", "grace@example.com", "Also great.");
		withConsent.setConsentGiven(true);
		testimonialRepository.save(withConsent);

		inboxService.setWallOfLove(owner.getId(), withoutConsent.getId(), true);

		assertThat(inboxService.get(owner.getId(), withoutConsent.getId()).isWallOfLove()).isTrue();
		assertThat(inboxService.get(owner.getId(), withConsent.getId()).isWallOfLove()).isFalse();
	}

	@Test
	@DisplayName("a deleted testimonial is excluded from the count")
	void deletedTestimonialsAreNotCounted() {
		Testimonial testimonial = testimonial("Ada", "ada@example.com", "Great.");
		testimonial("Grace", "grace@example.com", "Also great.");

		inboxService.delete(owner.getId(), testimonial.getId());

		assertThat(list().getTotalElements()).isEqualTo(1);
	}

	/**
	 * The whole inbox, unfiltered.
	 */
	private Page<Testimonial> list() {
		return list(null, null, null, null, null);
	}

	/**
	 * The inbox with some filters applied.
	 */
	private Page<Testimonial> list(String term, Boolean favorite, Boolean wallOfLove, Boolean hidden) {
		return list(term, favorite, wallOfLove, hidden, null);
	}

	/**
	 * The inbox with some filters applied.
	 */
	private Page<Testimonial> list(String term, Boolean favorite, Boolean wallOfLove, Boolean hidden,
			Short rating) {

		return inboxService.list(owner.getId(), space.getId(), term, favorite, wallOfLove, hidden,
				rating, PageRequest.of(0, 50));
	}

	/**
	 * A testimonial in the owner's Space.
	 */
	private Testimonial testimonial(String name, String email, String message) {
		return testimonial(name, email, message, null);
	}

	/**
	 * A testimonial in the owner's Space.
	 */
	private Testimonial testimonial(String name, String email, String message, Short rating) {
		return testimonialIn(space, name, email, message, rating);
	}

	/**
	 * A testimonial in a given Space.
	 */
	private Testimonial testimonialIn(Space target, String name, String email, String message,
			Short rating) {

		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(target);
		testimonial.setName(name);
		testimonial.setEmail(email);
		testimonial.setMessage(message);
		testimonial.setRating(rating);
		return testimonialRepository.save(testimonial);
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

	/**
	 * Sets a testimonial's submission time.
	 *
	 * <p>The column is {@code updatable = false} and Hibernate stamps it on
	 * insert, so it is written with a statement of its own. The ordering tests
	 * need distinct times: three testimonials inserted in one transaction can
	 * share a timestamp, which leaves "newest first" ambiguous.
	 */
	private void submittedAt(Testimonial testimonial, java.time.Instant when) {
		jdbcTemplate.update("update testimonials set created_at = ? where id = ?",
				java.sql.Timestamp.from(when), testimonial.getId());
	}

	/**
	 * A Space written straight to the database, past the plan's limit.
	 *
	 * <p>This is the state a user is left in when they downgrade: they still
	 * have more Spaces than the plan allows.
	 */
	private Space spaceDirectly(AppUser user, String title) {
		Space space = new Space();
		space.setOwner(user);
		space.setTitle(title);
		space.setSlug(title.toLowerCase().replace(' ', '-'));
		space.setThemeKey("light");
		space.setFormConfig(new java.util.LinkedHashMap<>());
		space.setEmbedConfig(new java.util.LinkedHashMap<>());
		space.setPublicProfileFields(new java.util.ArrayList<>());
		return spaceRepository.save(space);
	}
}
