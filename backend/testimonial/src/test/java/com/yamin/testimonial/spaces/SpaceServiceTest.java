package com.yamin.testimonial.spaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.billing.Plan;
import com.yamin.testimonial.billing.PlanLimits;
import com.yamin.testimonial.billing.Subscription;
import com.yamin.testimonial.billing.SubscriptionRepository;
import com.yamin.testimonial.billing.SubscriptionStatus;
import com.yamin.testimonial.common.exception.ConflictException;
import com.yamin.testimonial.common.exception.NotFoundException;
import com.yamin.testimonial.common.exception.ValidationException;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * Managing Spaces (PRD 4.2, 4.3, 4.4).
 *
 * <p>Runs against a real PostgreSQL: slug uniqueness is enforced by a unique
 * index, and the slug lock depends on the testimonials that actually exist.
 */
@SpringBootTest
@ActiveProfiles("test")
class SpaceServiceTest extends PostgresTestContainer {

	@Autowired
	private SpaceService spaceService;

	@Autowired
	private SpaceRepository spaceRepository;

	@Autowired
	private TestimonialRepository testimonialRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private PlanLimits planLimits;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		subscriptionRepository.deleteAll();
		appUserRepository.deleteAll();
	}

	@Test
	@DisplayName("creating a Space makes an unverified-slug from the title")
	void createGeneratesSlugFromTitle() {
		AppUser owner = verifiedUser("owner@example.com");

		Space space = spaceService.create(owner.getId(), "Acme Corp Reviews", "What our customers say",
				null, "light", false, null);

		assertThat(space.getSlug()).isEqualTo("acme-corp-reviews");
		assertThat(space.getTitle()).isEqualTo("Acme Corp Reviews");
		assertThat(space.getSubtitle()).isEqualTo("What our customers say");
		assertThat(space.getThemeKey()).isEqualTo("light");
		assertThat(space.isRatingEnabled()).isFalse();
	}

	@Test
	@DisplayName("the generated slug folds accents and drops punctuation")
	void createNormalisesTheSlug() {
		AppUser owner = verifiedUser("owner@example.com");

		Space space = spaceService.create(owner.getId(), "Café Déjà Vu — Reviews!", null,
				null, "light", false, null);

		assertThat(space.getSlug()).isEqualTo("cafe-deja-vu-reviews");
	}

	@Test
	@DisplayName("a supplied slug is used as given")
	void createUsesTheSuppliedSlug() {
		AppUser owner = verifiedUser("owner@example.com");

		Space space = spaceService.create(owner.getId(), "Acme Reviews", null,
				"my-custom-url", "light", false, null);

		assertThat(space.getSlug()).isEqualTo("my-custom-url");
	}

	@Test
	@DisplayName("a Space with no theme gets the first basic one")
	void createDefaultsToTheFirstBasicTheme() {
		AppUser owner = verifiedUser("owner@example.com");

		Space space = spaceService.create(owner.getId(), "Acme Reviews", null,
				null, null, false, null);

		assertThat(space.getThemeKey()).isEqualTo("light");
	}

	@Test
	@DisplayName("a slug that is already taken is rejected")
	void duplicateSlugIsRejected() {
		AppUser owner = verifiedUser("owner@example.com");
		spaceService.create(owner.getId(), "Acme Reviews", null, "taken", "light", false, null);

		assertThatThrownBy(() -> spaceService.create(owner.getId(), "Other Reviews", null,
				"taken", "light", false, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(error -> assertThat(((ValidationException) error).getFieldErrors())
						.containsEntry("slug", "That URL is already taken"));
	}

	@Test
	@DisplayName("a slug is unique across the platform, not just per user")
	void slugIsUniqueAcrossUsers() {
		AppUser first = verifiedUser("first@example.com");
		AppUser second = verifiedUser("second@example.com");
		spaceService.create(first.getId(), "Acme Reviews", null, "shared", "light", false, null);

		assertThatThrownBy(() -> spaceService.create(second.getId(), "Other Reviews", null,
				"shared", "light", false, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(error -> assertThat(((ValidationException) error).getFieldErrors())
						.containsEntry("slug", "That URL is already taken"));
	}

	@Test
	@DisplayName("the Free plan allows three Spaces and rejects the fourth")
	void freePlanSpaceLimitIsEnforced() {
		AppUser owner = verifiedUser("owner@example.com");

		for (int i = 1; i <= PlanLimits.FREE_SPACE_LIMIT; i++) {
			spaceService.create(owner.getId(), "Space " + i, null, "space-" + i, "light", false, null);
		}

		assertThatThrownBy(() -> spaceService.create(owner.getId(), "Space 4", null,
				"space-4", "light", false, null))
				.isInstanceOf(PlanLimitException.class)
				.hasMessageContaining("3 Spaces");
	}

	@Test
	@DisplayName("a Premium user may own more than three Spaces")
	void premiumPlanHasAHigherSpaceLimit() {
		AppUser owner = verifiedUser("owner@example.com");
		premium(owner);

		for (int i = 1; i <= PlanLimits.FREE_SPACE_LIMIT + 1; i++) {
			spaceService.create(owner.getId(), "Space " + i, null, "space-" + i, "light", false, null);
		}

		assertThat(spaceService.list(owner.getId())).hasSize(PlanLimits.FREE_SPACE_LIMIT + 1);
	}

	@Test
	@DisplayName("an unverified account cannot create a Space")
	void unverifiedAccountCannotCreateASpace() {
		AppUser owner = verifiedUser("owner@example.com");
		owner.setEmailVerified(false);
		appUserRepository.save(owner);

		assertThatThrownBy(() -> spaceService.create(owner.getId(), "Acme Reviews", null,
				"acme", "light", false, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(error -> assertThat(((ValidationException) error).getFieldErrors())
						.containsEntry("emailVerified",
								"Confirm your email address before creating a Space"));
	}

	@Test
	@DisplayName("a premium theme is rejected for a Free user")
	void premiumThemeIsRejectedOnFree() {
		AppUser owner = verifiedUser("owner@example.com");

		assertThatThrownBy(() -> spaceService.create(owner.getId(), "Acme Reviews", null,
				"acme", "midnight", false, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(error -> assertThat(((ValidationException) error).getFieldErrors())
						.containsEntry("themeKey", "That theme is part of the Premium plan"));
	}

	@Test
	@DisplayName("a premium theme is accepted for a Premium user")
	void premiumThemeIsAcceptedOnPremium() {
		AppUser owner = verifiedUser("owner@example.com");
		premium(owner);

		Space space = spaceService.create(owner.getId(), "Acme Reviews", null,
				"acme", "midnight", false, null);

		assertThat(space.getThemeKey()).isEqualTo("midnight");
	}

	@Test
	@DisplayName("an unknown theme is rejected")
	void unknownThemeIsRejected() {
		AppUser owner = verifiedUser("owner@example.com");

		assertThatThrownBy(() -> spaceService.create(owner.getId(), "Acme Reviews", null,
				"acme", "no-such-theme", false, null))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	@DisplayName("a Space keeps rendering a premium theme after a downgrade")
	void premiumThemeSurvivesADowngrade() {
		AppUser owner = verifiedUser("owner@example.com");
		premium(owner);

		Space space = spaceService.create(owner.getId(), "Acme Reviews", null,
				"acme", "midnight", false, null);

		downgrade(owner);

		// The Space still uses the theme it was created with.
		assertThat(spaceService.get(owner.getId(), space.getId()).getThemeKey()).isEqualTo("midnight");

		// But choosing a premium theme again is blocked.
		assertThatThrownBy(() -> spaceService.update(owner.getId(), space.getId(),
				null, null, null, "sunset", null, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(error -> assertThat(((ValidationException) error).getFieldErrors())
						.containsEntry("themeKey", "That theme is part of the Premium plan"));
	}

	@Test
	@DisplayName("the slug can be changed until the Space has received a testimonial")
	void slugIsEditableBeforeTheFirstSubmission() {
		AppUser owner = verifiedUser("owner@example.com");
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, "acme", "light", false, null);

		Space updated = spaceService.update(owner.getId(), space.getId(),
				null, null, "acme-renamed", null, null, null);

		assertThat(updated.getSlug()).isEqualTo("acme-renamed");
	}

	@Test
	@DisplayName("the slug is locked once the Space has received a testimonial")
	void slugIsLockedAfterTheFirstSubmission() {
		AppUser owner = verifiedUser("owner@example.com");
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, "acme", "light", false, null);
		addTestimonial(space, "Ada Lovelace", "ada@example.com");

		assertThatThrownBy(() -> spaceService.update(owner.getId(), space.getId(),
				null, null, "acme-renamed", null, null, null))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("cannot be changed");
	}

	@Test
	@DisplayName("a locked Space keeps its slug when it is saved with other changes")
	void lockedSlugIsUnchangedByOtherEdits() {
		AppUser owner = verifiedUser("owner@example.com");
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, "acme", "light", false, null);
		addTestimonial(space, "Ada Lovelace", "ada@example.com");

		Space updated = spaceService.update(owner.getId(), space.getId(),
				"Acme Reviews Renamed", null, null, null, true, null);

		assertThat(updated.getSlug()).isEqualTo("acme");
		assertThat(updated.getTitle()).isEqualTo("Acme Reviews Renamed");
	}

	@Test
	@DisplayName("another user's Space is not found")
	void anotherUsersSpaceIsNotFound() {
		AppUser owner = verifiedUser("owner@example.com");
		AppUser other = verifiedUser("other@example.com");
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, "acme", "light", false, null);

		assertThatThrownBy(() -> spaceService.get(other.getId(), space.getId()))
				.isInstanceOf(NotFoundException.class)
				.hasMessage("Space not found");

		assertThatThrownBy(() -> spaceService.update(other.getId(), space.getId(),
				"Hijacked", null, null, null, null, null))
				.isInstanceOf(NotFoundException.class);

		assertThatThrownBy(() -> spaceService.delete(other.getId(), space.getId()))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	@DisplayName("a user only ever lists their own Spaces")
	void listReturnsOnlyTheUsersSpaces() {
		AppUser owner = verifiedUser("owner@example.com");
		AppUser other = verifiedUser("other@example.com");
		spaceService.create(owner.getId(), "Mine One", null, "mine-one", "light", false, null);
		spaceService.create(owner.getId(), "Mine Two", null, "mine-two", "light", false, null);
		spaceService.create(other.getId(), "Theirs", null, "theirs", "light", false, null);

		assertThat(spaceService.list(owner.getId())).hasSize(2);
		assertThat(spaceService.list(other.getId())).hasSize(1);
	}

	@Test
	@DisplayName("deleting a Space deletes its testimonials too")
	void deleteRemovesTheTestimonials() {
		AppUser owner = verifiedUser("owner@example.com");
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, "acme", "light", false, null);
		addTestimonial(space, "Ada Lovelace", "ada@example.com");

		spaceService.delete(owner.getId(), space.getId());

		assertThat(spaceRepository.findById(space.getId())).isEmpty();
		assertThat(testimonialRepository.countBySpaceId(space.getId())).isZero();
		// The public URL goes with it.
		assertThat(spaceRepository.findBySlug("acme")).isEmpty();
	}

	@Test
	@DisplayName("the oldest three Spaces stay editable after a downgrade and the rest do not")
	void readOnlySpacesAreTheNewestOnesAfterADowngrade() {
		AppUser owner = verifiedUser("owner@example.com");
		premium(owner);

		List<Space> spaces = new java.util.ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			spaces.add(spaceService.create(owner.getId(), "Space " + i, null, "space-" + i, "light", false, null));
		}

		downgrade(owner);

		// The oldest three remain editable.
		for (int i = 0; i < PlanLimits.FREE_SPACE_LIMIT; i++) {
			Space space = spaces.get(i);
			assertThat(spaceService.isReadOnly(owner.getId(), space)).isFalse();
		}

		// The rest are read-only.
		for (int i = PlanLimits.FREE_SPACE_LIMIT; i < spaces.size(); i++) {
			Space space = spaces.get(i);
			assertThat(spaceService.isReadOnly(owner.getId(), space)).isTrue();

			assertThatThrownBy(() -> spaceService.update(owner.getId(), space.getId(),
					"Renamed", null, null, null, null, null))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("read-only");
		}
	}

	@Test
	@DisplayName("a read-only Space is still viewable")
	void readOnlySpacesRemainViewable() {
		AppUser owner = verifiedUser("owner@example.com");
		premium(owner);

		List<Space> spaces = new java.util.ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			spaces.add(spaceService.create(owner.getId(), "Space " + i, null, "space-" + i, "light", false, null));
		}
		downgrade(owner);

		Space newest = spaces.get(4);
		assertThat(spaceService.get(owner.getId(), newest.getId()).getId()).isEqualTo(newest.getId());
	}

	@Test
	@DisplayName("the form configuration keeps only the configurable fields")
	void formConfigKeepsOnlyConfigurableFields() {
		AppUser owner = verifiedUser("owner@example.com");

		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, "acme", "light", false,
				Map.of("companyName", Map.of("enabled", true, "required", true),
						"jobTitle", Map.of("enabled", false, "required", true)));

		assertThat(space.getFormConfig()).containsOnlyKeys("companyName", "jobTitle",
				"websiteUrl", "socialLinks");

		// A field that is off cannot be required.
		assertThat(space.getFormConfig().get("jobTitle")).isEqualTo(Map.of("enabled", false, "required", false));
		assertThat(space.getFormConfig().get("companyName")).isEqualTo(Map.of("enabled", true, "required", true));
	}

	@Test
	@DisplayName("the default form configuration collects every optional field")
	void defaultFormConfigEnablesEveryOptionalField() {
		AppUser owner = verifiedUser("owner@example.com");

		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, "acme", "light", false, null);

		for (FormField field : FormField.configurableFields()) {
			assertThat(space.getFormConfig().get(field.getKey()))
					.isEqualTo(Map.of("enabled", true, "required", false));
		}
	}

	@Test
	@DisplayName("editing the form configuration leaves existing testimonials alone")
	void editingTheFormDoesNotAlterExistingTestimonials() {
		AppUser owner = verifiedUser("owner@example.com");
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, "acme", "light", false, null);
		Testimonial testimonial = addTestimonial(space, "Ada Lovelace", "ada@example.com");

		spaceService.update(owner.getId(), space.getId(), null, null, null, null, null,
				Map.of("companyName", Map.of("enabled", false, "required", false)));

		Testimonial reloaded = testimonialRepository.findById(testimonial.getId()).orElseThrow();
		assertThat(reloaded.getName()).isEqualTo("Ada Lovelace");
		assertThat(reloaded.getMessage()).isEqualTo("A great product.");
	}

	/**
	 * Creates a verified account.
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
	 * Puts the user on the Premium plan.
	 */
	private void premium(AppUser user) {
		Subscription subscription = new Subscription();
		subscription.setUser(user);
		subscription.setPlan(Plan.PREMIUM);
		subscription.setStatus(SubscriptionStatus.ACTIVE);
		subscriptionRepository.save(subscription);
	}

	/**
	 * Puts the user back on the Free plan.
	 */
	private void downgrade(AppUser user) {
		Subscription subscription = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
		subscription.setPlan(Plan.FREE);
		subscriptionRepository.save(subscription);
	}

	/**
	 * Adds a testimonial to a Space.
	 */
	private Testimonial addTestimonial(Space space, String name, String email) {
		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(space);
		testimonial.setName(name);
		testimonial.setEmail(email);
		testimonial.setMessage("A great product.");
		return testimonialRepository.save(testimonial);
	}
}
