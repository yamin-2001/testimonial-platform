package com.yamin.testimonial.embed;

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
import com.yamin.testimonial.billing.Subscription;
import com.yamin.testimonial.billing.SubscriptionRepository;
import com.yamin.testimonial.billing.SubscriptionStatus;
import com.yamin.testimonial.common.exception.ConflictException;
import com.yamin.testimonial.common.exception.NotFoundException;
import com.yamin.testimonial.common.exception.ValidationException;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * A Space's embed: its configuration and its content (PRD 4.8).
 *
 * <p>Runs against a real PostgreSQL, because the content of an embed depends
 * on the Wall of Love and hidden flags in the database rather than on
 * anything held in memory.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmbedServiceTest extends PostgresTestContainer {

	@Autowired
	private EmbedService embedService;

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

	private AppUser owner;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		subscriptionRepository.deleteAll();
		appUserRepository.deleteAll();

		owner = verifiedUser("owner@example.com");
	}

	@Test
	@DisplayName("a new Space starts with the free embed settings")
	void aNewSpaceStartsWithTheFreeSettings() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		EmbedConfiguration configuration = embedService.configurationOf(owner.getId(), space.getId());

		assertThat(configuration.settings())
				.containsEntry("layout", "masonry")
				.containsEntry("mode", "light")
				.containsEntry("backgroundColor", "")
				.containsEntry("animation", false);
	}

	@Test
	@DisplayName("a Free user can save the masonry layout and the mode")
	void aFreeUserCanSaveTheFreeSettings() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		EmbedConfiguration configuration = embedService.save(owner.getId(), space.getId(),
				"masonry", "dark", null, null, null);

		assertThat(configuration.settings())
				.containsEntry("layout", "masonry")
				.containsEntry("mode", "dark");
	}

	@Test
	@DisplayName("the carousel layout is rejected for a Free user")
	void theCarouselIsRejectedForAFreeUser() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		assertThatThrownBy(() -> embedService.save(owner.getId(), space.getId(), "carousel", null,
				null, null, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(
						((ValidationException) thrown).getFieldErrors().get("layout"))
						.contains("Premium"));
	}

	@Test
	@DisplayName("a background colour is rejected for a Free user")
	void aBackgroundColourIsRejectedForAFreeUser() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		assertThatThrownBy(() -> embedService.save(owner.getId(), space.getId(), null, null,
				"#1f2937", null, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(
						((ValidationException) thrown).getFieldErrors().get("backgroundColor"))
						.contains("Premium"));
	}

	@Test
	@DisplayName("animation is rejected for a Free user")
	void animationIsRejectedForAFreeUser() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		assertThatThrownBy(() -> embedService.save(owner.getId(), space.getId(), null, null, null,
				true, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(
						((ValidationException) thrown).getFieldErrors().get("animation"))
						.contains("Premium"));
	}

	@Test
	@DisplayName("a Premium user can save the premium settings")
	void aPremiumUserCanSaveThePremiumSettings() {
		premium(owner);
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		EmbedConfiguration configuration = embedService.save(owner.getId(), space.getId(),
				"carousel", "dark", "#1f2937", true, null);

		assertThat(configuration.settings())
				.containsEntry("layout", "carousel")
				.containsEntry("mode", "dark")
				.containsEntry("backgroundColor", "#1f2937")
				.containsEntry("animation", true);
	}

	@Test
	@DisplayName("a background colour that is not a colour is rejected")
	void aBackgroundColourThatIsNotAColourIsRejected() {
		premium(owner);
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		assertThatThrownBy(() -> embedService.save(owner.getId(), space.getId(), null, null,
				"red; background-image: url(https://evil.example.com)", null, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(
						((ValidationException) thrown).getFieldErrors().get("backgroundColor"))
						.contains("colour"));
	}

	@Test
	@DisplayName("an unknown layout is rejected")
	void anUnknownLayoutIsRejected() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		assertThatThrownBy(() -> embedService.save(owner.getId(), space.getId(), "spiral", null, null,
				null, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(
						((ValidationException) thrown).getFieldErrors().get("layout"))
						.contains("Unknown layout"));
	}

	@Test
	@DisplayName("after a downgrade the saved premium settings are still kept")
	void afterADowngradeTheSavedPremiumSettingsAreStillKept() {
		premium(owner);
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);
		embedService.save(owner.getId(), space.getId(), "carousel", "dark", "#1f2937", true, null);

		downgrade(owner);

		// The settings are read back unchanged.
		EmbedConfiguration configuration = embedService.configurationOf(owner.getId(), space.getId());
		assertThat(configuration.settings())
				.containsEntry("layout", "carousel")
				.containsEntry("backgroundColor", "#1f2937")
				.containsEntry("animation", true);

		// But changing to a premium option is blocked, while a free one is
		// still allowed.
		assertThatThrownBy(() -> embedService.save(owner.getId(), space.getId(), null, null, null,
				true, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(thrown -> assertThat(
						((ValidationException) thrown).getFieldErrors().get("animation"))
						.contains("Premium"));

		EmbedConfiguration afterFreeChange = embedService.save(owner.getId(), space.getId(),
				"masonry", null, null, null, null);
		assertThat(afterFreeChange.settings())
				.containsEntry("layout", "masonry")
				// The premium settings that were not changed are still there.
				.containsEntry("backgroundColor", "#1f2937")
				.containsEntry("animation", true);
	}

	@Test
	@DisplayName("another user's Space is not found")
	void anotherUsersSpaceIsNotFound() {
		AppUser other = verifiedUser("other@example.com");
		Space space = spaceService.create(other.getId(), "Theirs", null, null, null, false, null);

		assertThatThrownBy(() -> embedService.configurationOf(owner.getId(), space.getId()))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	@DisplayName("the embed shows only the testimonials on the Wall of Love")
	void theEmbedShowsOnlyTheWallOfLove() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		Testimonial onTheWall = testimonial(space, "Ada", "Great.", true, false);
		testimonial(space, "Grace", "Good.", false, false);

		EmbedContent content = embedService.contentBySlug(space.getSlug());

		assertThat(content.testimonials()).hasSize(1);
		assertThat(content.testimonials().get(0).id()).isEqualTo(onTheWall.getId());
	}

	@Test
	@DisplayName("a hidden testimonial is not in the embed")
	void aHiddenTestimonialIsNotInTheEmbed() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		testimonial(space, "Ada", "Great.", true, true);
		testimonial(space, "Grace", "Good.", true, false);

		EmbedContent content = embedService.contentBySlug(space.getSlug());

		assertThat(content.testimonials()).hasSize(1);
		assertThat(content.testimonials().get(0).name()).isEqualTo("Grace");
	}

	@Test
	@DisplayName("the embed never exposes the submitter's email")
	void theEmbedNeverExposesTheEmail() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		testimonial(space, "Ada", "Great.", true, false);

		EmbedContent content = embedService.contentBySlug(space.getSlug());

		assertThat(content.testimonials().get(0)).doesNotHaveToString("ada@example.com");
	}

	@Test
	@DisplayName("a profile field is only published when the Space publishes it")
	void aProfileFieldIsOnlyPublishedWhenTheSpacePublishesIt() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(space);
		testimonial.setName("Ada");
		testimonial.setEmail("ada@example.com");
		testimonial.setMessage("Great.");
		testimonial.setCompanyName("Acme");
		testimonial.setJobTitle("Engineer");
		testimonial.setWebsiteUrl("https://ada.example.com");
		testimonial.setSocialLinks(List.of("https://social.example.com/ada"));
		testimonial.setWallOfLove(true);
		testimonialRepository.save(testimonial);

		// Nothing is published yet.
		EmbedTestimonial bare = embedService.contentBySlug(space.getSlug()).testimonials().get(0);
		assertThat(bare.companyName()).isNull();
		assertThat(bare.jobTitle()).isNull();
		assertThat(bare.websiteUrl()).isNull();
		assertThat(bare.socialLinks()).isEmpty();

		// The company name is published.
		embedService.save(owner.getId(), space.getId(), null, null, null, null,
				List.of("companyName"));

		EmbedTestimonial withCompany = embedService.contentBySlug(space.getSlug()).testimonials().get(0);
		assertThat(withCompany.companyName()).isEqualTo("Acme");
		assertThat(withCompany.jobTitle()).isNull();
	}

	@Test
	@DisplayName("the content carries the Space's settings")
	void theContentCarriesTheSpacesSettings() {
		premium(owner);
		Space space = spaceService.create(owner.getId(), "Acme Reviews", "What our customers say",
				null, null, false, null);
		embedService.save(owner.getId(), space.getId(), "carousel", "dark", "#101010", true, null);

		EmbedContent content = embedService.contentBySlug(space.getSlug());

		assertThat(content.title()).isEqualTo("Acme Reviews");
		assertThat(content.subtitle()).isEqualTo("What our customers say");
		assertThat(content.settings())
				.containsEntry("layout", "carousel")
				.containsEntry("mode", "dark")
				.containsEntry("backgroundColor", "#101010")
				.containsEntry("animation", true);
	}

	@Test
	@DisplayName("an unknown slug is not found")
	void anUnknownSlugIsNotFound() {
		assertThatThrownBy(() -> embedService.contentBySlug("no-such-space"))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	@DisplayName("a read-only Space refuses a configuration change")
	void aReadOnlySpaceRefusesAConfigurationChange() {
		// The Free plan allows three Spaces, so the fourth is written straight
		// to the database: that is the state a user is left in when they
		// downgrade, and the state the read-only rule exists for.
		for (int i = 1; i <= 3; i++) {
			spaceService.create(owner.getId(), "Space " + i, null, null, null, false, null);
		}
		Space fourth = spaceDirectly(owner, "Space 4");

		assertThatThrownBy(() -> embedService.save(owner.getId(), fourth.getId(), "masonry", null,
				null, null, null))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("read-only");
	}

	@Test
	@DisplayName("only the known profile fields are published")
	void onlyTheKnownProfileFieldsArePublished() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		EmbedConfiguration configuration = embedService.save(owner.getId(), space.getId(), null, null,
				null, null, List.of("companyName", "email", "secretField"));

		assertThat(configuration.publicProfileFields())
				.extracting(PublicProfileFieldState::key)
				.containsExactly("companyName", "jobTitle", "websiteUrl", "socialLinks");
		assertThat(configuration.publicProfileFields())
				.filteredOn(state -> state.enabled())
				.extracting(PublicProfileFieldState::key)
				.containsExactly("companyName");
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
	 * Takes the user back to the Free plan.
	 */
	private void downgrade(AppUser user) {
		subscriptionRepository.findByUserId(user.getId()).ifPresent(subscription -> {
			subscription.setStatus(SubscriptionStatus.CANCELED);
			subscriptionRepository.save(subscription);
		});
	}

	@Test
	@DisplayName("the embed is capped, so one Space cannot make it return everything")
	void theEmbedIsCapped() {
		Space space = spaceService.create(owner.getId(), "Acme Reviews", null, null, null, false,
				null);

		// More than the cap, all of them on the Wall of Love and visible.
		for (int i = 0; i < EmbedService.EMBED_LIMIT + 5; i++) {
			testimonial(space, "Name " + i, "Message " + i, true, false);
		}

		EmbedContent content = embedService.contentBySlug(space.getSlug());

		assertThat(content.testimonials()).hasSize(EmbedService.EMBED_LIMIT);
	}

	/**
	 * A testimonial in a Space.
	 */
	private Testimonial testimonial(Space space, String name, String message, boolean wallOfLove,
			boolean hidden) {

		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(space);
		testimonial.setName(name);
		testimonial.setEmail(name.toLowerCase() + "@example.com");
		testimonial.setMessage(message);
		testimonial.setWallOfLove(wallOfLove);
		testimonial.setHidden(hidden);
		return testimonialRepository.save(testimonial);
	}

	/**
	 * A Space written straight to the database, past the plan's limit.
	 */
	private Space spaceDirectly(AppUser user, String title) {
		Space space = new Space();
		space.setOwner(user);
		space.setTitle(title);
		space.setSlug(title.toLowerCase().replace(' ', '-'));
		space.setThemeKey("light");
		space.setFormConfig(new java.util.LinkedHashMap<>());
		space.setEmbedConfig(EmbedSettings.defaults());
		space.setPublicProfileFields(new java.util.ArrayList<>());
		return spaceRepository.save(space);
	}
}
