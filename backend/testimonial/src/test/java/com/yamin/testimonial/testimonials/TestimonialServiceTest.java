package com.yamin.testimonial.testimonials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

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
import com.yamin.testimonial.testimonials.web.SubmissionDtos.SubmissionRequest;

/**
 * Receiving a testimonial from a public page (PRD 4.5).
 *
 * <p>Runs against a real PostgreSQL, because the per-Space limit depends on
 * what is actually in the table and the submission is written with the
 * schema's own constraints.
 *
 * <p>The rate limit is turned up for this class. The limiter is keyed by the
 * caller's IP address, and every request from the test machine shares one, so
 * the default capacity of five would throttle the class's own traffic. The
 * limit itself is proved by {@code SubmissionRateLimitTest}, which has its
 * own application context and its own limiter.
 */
@SpringBootTest(properties = { "app.submission.rate-limit-capacity=1000",
		"app.submission.rate-limit-refill-seconds=600" })
@ActiveProfiles("test")
class TestimonialServiceTest extends PostgresTestContainer {

	@Autowired
	private TestimonialService testimonialService;

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
	private RateLimiter rateLimiter;

	private AppUser owner;

	@BeforeEach
	void reset() {
		testimonialRepository.deleteAll();
		spaceRepository.deleteAll();
		subscriptionRepository.deleteAll();
		appUserRepository.deleteAll();

		// The limiter is keyed by IP address and every request from the test
		// machine shares one, so each test starts with a fresh limiter.
		rateLimiter.reset();

		owner = verifiedUser("owner@example.com");
	}

	@Test
	@DisplayName("a submission is stored with the values it carried")
	void submissionIsStored() {
		Space space = space("Acme Reviews");

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada Lovelace", "ada@example.com", "A great product.").build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getSpace().getId()).isEqualTo(space.getId());
		assertThat(saved.getName()).isEqualTo("Ada Lovelace");
		assertThat(saved.getEmail()).isEqualTo("ada@example.com");
		assertThat(saved.getMessage()).isEqualTo("A great product.");
		assertThat(saved.getSubmitterIp()).isNotNull();
		assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
	}

	@Test
	@DisplayName("a new testimonial is visible and not on the Wall of Love")
	void newTestimonialsAreVisibleByDefault() {
		Space space = space("Acme Reviews");

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.").build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved.isHidden()).isFalse();
		assertThat(saved.isFavorite()).isFalse();
		assertThat(saved.isWallOfLove()).isFalse();
	}

	@Test
	@DisplayName("the consent box is stored and never blocks a submission")
	void consentIsStoredButNeverRequired() {
		Space space = space("Acme Reviews");

		Testimonial withoutConsent = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.").build(),
				"203.0.113.9", "Mozilla/5.0");
		assertThat(withoutConsent.isConsentGiven()).isFalse();

		Testimonial withConsent = testimonialService.submit(space.getSlug(),
				submission("Grace", "grace@example.com", "Also great.")
						.withConsent(true).build(),
				"203.0.113.10", "Mozilla/5.0");
		assertThat(withConsent.isConsentGiven()).isTrue();
	}

	@Test
	@DisplayName("a missing name is rejected")
	void missingNameIsRejected() {
		Space space = space("Acme Reviews");

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission(null, "ada@example.com", "Great.").build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsEntry("name", "Your name is required"));
	}

	@Test
	@DisplayName("a missing email is rejected")
	void missingEmailIsRejected() {
		Space space = space("Acme Reviews");

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission("Ada", null, "Great.").build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsKey("email"));
	}

	@Test
	@DisplayName("a missing testimonial is rejected")
	void missingMessageIsRejected() {
		Space space = space("Acme Reviews");

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", null).build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsKey("message"));
	}

	@Test
	@DisplayName("every missing field is reported at once")
	void everyMissingFieldIsReportedTogether() {
		Space space = space("Acme Reviews");

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission(null, null, null).build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsKeys("name", "email", "message"));
	}

	@Test
	@DisplayName("a malformed email is rejected")
	void malformedEmailIsRejected() {
		Space space = space("Acme Reviews");

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission("Ada", "not-an-email", "Great.").build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsKey("email"));
	}

	@Test
	@DisplayName("a testimonial longer than 2000 characters is rejected")
	void longTestimonialIsRejected() {
		Space space = space("Acme Reviews");

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "x".repeat(2001)).build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsKey("message"));
	}

	@Test
	@DisplayName("a field the Space switched off is not stored")
	void disabledFieldIsNotStored() {
		Space space = spaceWithForm("Acme Reviews", Map.of(
				"companyName", Map.of("enabled", false, "required", false)));

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.")
						.withCompanyName("Acme Inc.").build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved.getCompanyName()).isNull();
	}

	@Test
	@DisplayName("a field the Space switched off is not an error either")
	void disabledFieldIsNotAnError() {
		Space space = spaceWithForm("Acme Reviews", Map.of(
				"companyName", Map.of("enabled", false, "required", false)));

		// The submission carries the field anyway. It is simply ignored.
		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.")
						.withCompanyName("Acme Inc.").build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved).isNotNull();
	}

	@Test
	@DisplayName("a required optional field must be filled in")
	void requiredOptionalFieldMustBePresent() {
		Space space = spaceWithForm("Acme Reviews", Map.of(
				"companyName", Map.of("enabled", true, "required", true)));

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.").build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsKey("companyName"));
	}

	@Test
	@DisplayName("a required optional field is stored when it is given")
	void requiredOptionalFieldIsStored() {
		Space space = spaceWithForm("Acme Reviews", Map.of(
				"companyName", Map.of("enabled", true, "required", true)));

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.")
						.withCompanyName("Acme Inc.").build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved.getCompanyName()).isEqualTo("Acme Inc.");
	}

	@Test
	@DisplayName("a website URL that is not http or https is rejected")
	void unsafeUrlIsRejected() {
		Space space = space("Acme Reviews");

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.")
						.withWebsiteUrl("javascript:alert(1)").build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsKey("websiteUrl"));
	}

	@Test
	@DisplayName("an https website URL is accepted")
	void httpsUrlIsAccepted() {
		Space space = space("Acme Reviews");

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.")
						.withWebsiteUrl("https://example.com/me").build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved.getWebsiteUrl()).isEqualTo("https://example.com/me");
	}

	@Test
	@DisplayName("social links are stored as a list")
	void socialLinksAreStored() {
		Space space = space("Acme Reviews");

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.")
						.withSocialLinks(List.of("https://linkedin.com/in/ada")).build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved.getSocialLinks()).containsExactly("https://linkedin.com/in/ada");
	}

	@Test
	@DisplayName("a rating outside 1 to 5 is rejected")
	void outOfRangeRatingIsRejected() {
		Space space = spaceWithRating("Acme Reviews", true);

		assertThatThrownBy(() -> testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.").withRating(6).build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ValidationException.class)
				.satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
						.containsKey("rating"));
	}

	@Test
	@DisplayName("a rating is stored when the Space shows the control")
	void ratingIsStored() {
		Space space = spaceWithRating("Acme Reviews", true);

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.").withRating(5).build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved.getRating()).isEqualTo((short) 5);
	}

	@Test
	@DisplayName("a rating is ignored when the Space does not show the control")
	void ratingIsIgnoredWhenTheControlIsHidden() {
		Space space = spaceWithRating("Acme Reviews", false);

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.").withRating(5).build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved.getRating()).isNull();
	}

	@Test
	@DisplayName("a submission that filled the honeypot stores nothing")
	void honeypotSubmissionStoresNothing() {
		Space space = space("Acme Reviews");

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", "Great.")
						.withHoneypot("https://spam.example.com").build(),
				"203.0.113.9", "Mozilla/5.0");

		assertThat(saved).isNull();
		assertThat(testimonialRepository.countBySpaceId(space.getId())).isZero();
	}

	@Test
	@DisplayName("markup in a testimonial is stored as it was written")
	void markupIsStoredVerbatim() {
		Space space = space("Acme Reviews");

		String markup = "<script>alert('x')</script>";

		Testimonial saved = testimonialService.submit(space.getSlug(),
				submission("Ada", "ada@example.com", markup).build(),
				"203.0.113.9", "Mozilla/5.0");

		// The content is stored as it was written; it is rendered inert.
		assertThat(saved.getMessage()).isEqualTo(markup);
	}

	@Test
	@DisplayName("an unknown slug is not found")
	void unknownSlugIsNotFound() {
		assertThatThrownBy(() -> testimonialService.submit("no-such-space",
				submission("Ada", "ada@example.com", "Great.").build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	@DisplayName("a Space that has reached its limit stops accepting submissions")
	void aSpaceAtItsLimitStopsAccepting() {
		AppUser freeOwner = verifiedUser("free@example.com");
		Space freeSpace = spaceFor(freeOwner, "Free Reviews");
		fillWithTestimonials(freeSpace, 100);

		assertThat(testimonialService.isAccepting(freeSpace)).isFalse();

		assertThatThrownBy(() -> testimonialService.submit(freeSpace.getSlug(),
				submission("Ada", "ada@example.com", "Great.").build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ConflictException.class)
				.hasMessage("This Space is not accepting responses right now.");
	}

	@Test
	@DisplayName("a Space below its limit accepts submissions")
	void aSpaceBelowItsLimitAccepts() {
		Space space = space("Acme Reviews");

		assertThat(testimonialService.isAccepting(space)).isTrue();
	}

	@Test
	@DisplayName("a read-only Space stops accepting submissions")
	void aReadOnlySpaceStopsAccepting() {
		AppUser freeOwner = verifiedUser("free@example.com");

		// The Free plan allows three Spaces, so the fourth cannot be created
		// through the service. A user who downgrades from Premium is left
		// with more Spaces than the plan allows, so the fourth is written
		// directly -- which is the state the read-only rule exists for.
		for (int i = 1; i <= 3; i++) {
			spaceFor(freeOwner, "Space " + i);
		}
		Space fourth = spaceDirectly(freeOwner, "Space 4");

		assertThat(testimonialService.isReadOnly(fourth)).isTrue();
		assertThat(testimonialService.isAccepting(fourth)).isFalse();

		assertThatThrownBy(() -> testimonialService.submit(fourth.getSlug(),
				submission("Ada", "ada@example.com", "Great.").build(),
				"203.0.113.9", "Mozilla/5.0"))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	@DisplayName("the oldest Spaces stay editable when the user has too many")
	void theOldestSpacesStayEditable() {
		AppUser freeOwner = verifiedUser("free@example.com");

		for (int i = 1; i <= 3; i++) {
			spaceFor(freeOwner, "Space " + i);
		}
		spaceDirectly(freeOwner, "Space 4");

		Space first = spaceRepository.findByOwnerIdOrderBySeqAsc(freeOwner.getId()).get(0);
		Space last = spaceRepository.findByOwnerIdOrderBySeqAsc(freeOwner.getId()).get(3);

		assertThat(testimonialService.isReadOnly(first)).isFalse();
		assertThat(testimonialService.isAccepting(first)).isTrue();
		assertThat(testimonialService.isReadOnly(last)).isTrue();
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
	 * A Space belonging to the owner.
	 */
	private Space space(String title) {
		return spaceFor(owner, title);
	}

	/**
	 * A Space belonging to the given owner.
	 */
	private Space spaceFor(AppUser user, String title) {
		return spaceService.create(user.getId(), title, null, null, null, false, null);
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

	/**
	 * A Space with a particular form configuration.
	 */
	private Space spaceWithForm(String title, Map<String, Object> formConfig) {
		return spaceService.create(owner.getId(), title, null, null, null, false, formConfig);
	}

	/**
	 * A Space with or without the rating control.
	 */
	private Space spaceWithRating(String title, boolean ratingEnabled) {
		return spaceService.create(owner.getId(), title, null, null, null, ratingEnabled, null);
	}

	/**
	 * Gives the Space enough testimonials to reach the Free limit.
	 */
	private void fillWithTestimonials(Space space, int count) {
		for (int i = 0; i < count; i++) {
			Testimonial testimonial = new Testimonial();
			testimonial.setSpace(space);
			testimonial.setName("Filler " + i);
			testimonial.setEmail("filler-" + i + "@example.com");
			testimonial.setMessage("A testimonial.");
			testimonialRepository.save(testimonial);
		}
	}

	/**
	 * A submission, built up so that each test only has to change the one
	 * thing it is about.
	 */
	private Submissions submission(String name, String email, String message) {
		return Submissions.of(name, email, message);
	}
}
