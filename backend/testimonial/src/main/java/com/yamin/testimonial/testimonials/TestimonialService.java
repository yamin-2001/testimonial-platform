package com.yamin.testimonial.testimonials;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.billing.PlanLimits;
import com.yamin.testimonial.common.exception.ConflictException;
import com.yamin.testimonial.common.exception.NotFoundException;
import com.yamin.testimonial.common.exception.ValidationException;
import com.yamin.testimonial.spaces.FormDefinition;
import com.yamin.testimonial.spaces.FormField;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.testimonials.config.SubmissionProperties;
import com.yamin.testimonial.testimonials.config.TurnstileProperties;
import com.yamin.testimonial.testimonials.web.SubmissionDtos.SubmissionRequest;

/**
 * Receiving a testimonial from a Space's public page (PRD 4.5).
 *
 * <p>Submitters never authenticate (PRD 6.4). The Space is found by its
 * public slug, and the rules about what the form collects come from
 * {@link FormDefinition} -- the same authority the preview and the wizard use,
 * so a field that is shown is a field that is accepted and a field that is
 * switched off is neither.
 *
 * <p>The checks run in a fixed order, and the order matters:
 * <ol>
 *   <li>the Space must exist (404);</li>
 *   <li>the Space must be accepting submissions (409);</li>
 *   <li>the submitter must not be over the rate limit (429);</li>
 *   <li>the honeypot must be empty;</li>
 *   <li>the CAPTCHA must pass, if one is configured;</li>
 *   <li>the values must be acceptable (400).</li>
 * </ol>
 * The rate limit comes before the CAPTCHA so that a bot cannot use the
 * CAPTCHA as an oracle, and the value checks come last so that the submitter
 * is told everything that is wrong with their submission in one go rather than
 * one field per attempt.
 */
@Service
public class TestimonialService {

	private static final Logger log = LoggerFactory.getLogger(TestimonialService.class);

	/** The longest a testimonial may be (PRD 4.5). */
	public static final int MESSAGE_MAX_LENGTH = 2000;

	/** The longest a name may be. */
	private static final int NAME_MAX_LENGTH = 200;

	/** The longest an email may be. */
	private static final int EMAIL_MAX_LENGTH = 255;

	/** The longest a single social link may be. */
	private static final int URL_MAX_LENGTH = 500;

	/** The most social links one submitter may give. */
	private static final int SOCIAL_LINKS_MAX_COUNT = 10;

	private final SpaceRepository spaceRepository;

	private final TestimonialRepository testimonialRepository;

	private final SpaceService spaceService;

	private final PlanLimits planLimits;

	private final RateLimiter rateLimiter;

	private final TurnstileVerifier turnstileVerifier;

	private final SubmissionProperties submissionProperties;

	private final TurnstileProperties turnstileProperties;

	public TestimonialService(SpaceRepository spaceRepository,
			TestimonialRepository testimonialRepository,
			SpaceService spaceService,
			PlanLimits planLimits,
			RateLimiter rateLimiter,
			TurnstileVerifier turnstileVerifier,
			SubmissionProperties submissionProperties,
			TurnstileProperties turnstileProperties) {

		this.spaceRepository = spaceRepository;
		this.testimonialRepository = testimonialRepository;
		this.spaceService = spaceService;
		this.planLimits = planLimits;
		this.rateLimiter = rateLimiter;
		this.turnstileVerifier = turnstileVerifier;
		this.submissionProperties = submissionProperties;
		this.turnstileProperties = turnstileProperties;
	}

	/**
	 * The Space behind a public slug.
	 *
	 * @throws NotFoundException if no Space has that slug
	 */
	@Transactional(readOnly = true)
	public Space findBySlug(String slug) {
		return spaceRepository.findBySlug(slug)
				.orElseThrow(() -> new NotFoundException("Space not found"));
	}

	/**
	 * Whether the Space is currently taking submissions.
	 *
	 * <p>A Space stops accepting when it has reached its plan's per-Space
	 * testimonial limit, or when it has become read-only (PRD 4.9).
	 */
	@Transactional(readOnly = true)
	public boolean isAccepting(Space space) {
		return !hasReachedLimit(space) && !isReadOnly(space);
	}

	/**
	 * Receives a testimonial for the Space behind a public slug.
	 *
	 * <p>The Space is looked up with its row locked, so that two concurrent
	 * submissions to the same Space cannot both read a count below the limit
	 * and both insert (PRD 4.9).
	 *
	 * @param slug the Space's public slug
	 * @param request the submitted values
	 * @param remoteIp the submitter's IP address, used for the rate limit and
	 *        stored with the testimonial
	 * @param userAgent the submitter's browser, stored for the owner's inbox
	 * @return the stored testimonial, or null when the honeypot was filled
	 * @throws NotFoundException if no Space has that slug
	 * @throws ConflictException if the Space is not accepting submissions
	 * @throws RateLimitException if the submitter has made too many
	 *         submissions
	 * @throws ValidationException if the submitted values are not acceptable
	 */
	@Transactional
	public Testimonial submit(String slug, SubmissionRequest request, String remoteIp,
			String userAgent) {

		Space space = spaceRepository.findBySlugForUpdate(slug)
				.orElseThrow(() -> new NotFoundException("Space not found"));

		if (!isAccepting(space)) {
			throw new ConflictException("This Space is not accepting responses right now.");
		}

		rateLimiter.consume(remoteIp);

		// A bot fills in the hidden field. It is answered as though the
		// submission succeeded, but nothing is stored, so the bot learns
		// nothing from the response.
		if (isHoneypotFilled(request)) {
			log.info("Discarded a submission to Space {} that filled the honeypot", space.getSlug());
			return null;
		}

		Map<String, String> errors = new LinkedHashMap<>();

		checkCaptcha(request, remoteIp, errors);

		Map<String, Object> formConfig = space.getFormConfig();
		List<FormField> accepted = FormDefinition.acceptedFields(formConfig);

		// A field that is switched off is not stored, even if the client sent
		// it (PRD 4.3). A field that is not accepted is also not an error:
		// the client simply sent more than the form asked for.
		Testimonial testimonial = new Testimonial();
		testimonial.setSpace(space);

		String name = trimmed(request.name());
		requireText(errors, FormField.NAME.getKey(), name, "Your name is required");
		checkLength(errors, FormField.NAME.getKey(), name, NAME_MAX_LENGTH,
				"Your name is too long");
		testimonial.setName(name);

		String email = trimmed(request.email());
		requireText(errors, FormField.EMAIL.getKey(), email, "Your email is required");
		checkEmail(errors, email);
		testimonial.setEmail(email);

		String message = trimmed(request.message());
		requireText(errors, FormField.MESSAGE.getKey(), message, "Your testimonial is required");
		checkLength(errors, FormField.MESSAGE.getKey(), message, MESSAGE_MAX_LENGTH,
				"Your testimonial must be at most 2000 characters");
		testimonial.setMessage(message);

		if (accepted.contains(FormField.COMPANY_NAME)) {
			String companyName = trimmed(request.companyName());
			requireIfRequired(errors, formConfig, FormField.COMPANY_NAME, companyName);
			checkLength(errors, FormField.COMPANY_NAME.getKey(), companyName, NAME_MAX_LENGTH,
					"Company name is too long");
			testimonial.setCompanyName(companyName);
		}

		if (accepted.contains(FormField.JOB_TITLE)) {
			String jobTitle = trimmed(request.jobTitle());
			requireIfRequired(errors, formConfig, FormField.JOB_TITLE, jobTitle);
			checkLength(errors, FormField.JOB_TITLE.getKey(), jobTitle, NAME_MAX_LENGTH,
					"Job title is too long");
			testimonial.setJobTitle(jobTitle);
		}

		if (accepted.contains(FormField.WEBSITE_URL)) {
			String websiteUrl = trimmed(request.websiteUrl());
			requireIfRequired(errors, formConfig, FormField.WEBSITE_URL, websiteUrl);
			checkUrl(errors, FormField.WEBSITE_URL.getKey(), websiteUrl);
			testimonial.setWebsiteUrl(websiteUrl);
		}

		if (accepted.contains(FormField.SOCIAL_LINKS)) {
			List<String> socialLinks = cleanedLinks(request.socialLinks());
			if (socialLinks != null) {
				requireIfRequired(errors, formConfig, FormField.SOCIAL_LINKS,
						String.join(", ", socialLinks));
				if (socialLinks.size() > SOCIAL_LINKS_MAX_COUNT) {
					errors.put(FormField.SOCIAL_LINKS.getKey(),
							"Please give at most " + SOCIAL_LINKS_MAX_COUNT + " links");
				} else {
					for (String link : socialLinks) {
						checkUrl(errors, FormField.SOCIAL_LINKS.getKey(), link);
					}
				}
				testimonial.setSocialLinks(socialLinks);
			}
		}

		if (accepted.contains(FormField.CONSENT)) {
			testimonial.setConsentGiven(Boolean.TRUE.equals(request.consent()));
		}

		// The rating is only collected when the Space shows the control
		// (PRD 4.5). A rating sent for a Space that does not show it is
		// ignored rather than an error.
		if (space.isRatingEnabled()) {
			Integer rating = request.rating();
			if (rating != null && (rating < 1 || rating > 5)) {
				errors.put("rating", "Choose a rating between 1 and 5");
			} else {
				testimonial.setRating(rating == null ? null : rating.shortValue());
			}
		}

		if (!errors.isEmpty()) {
			throw new ValidationException("Please correct the highlighted fields", errors);
		}

		testimonial.setSubmitterIp(toInetAddress(remoteIp));
		testimonial.setUserAgent(trimToNull(userAgent));

		// A new testimonial is visible by default and is not on the Wall of
		// Love until the owner says so (PRD 6.8).
		testimonial.setHidden(false);
		testimonial.setFavorite(false);
		testimonial.setWallOfLove(false);

		Testimonial saved = testimonialRepository.save(testimonial);
		log.info("Stored a testimonial for Space {}", space.getSlug());
		return saved;
	}

	/**
	 * Whether the rate limiter believes the {@code X-Forwarded-For} header.
	 *
	 * <p>Only true when a proxy is known to be in front; see
	 * {@link SubmissionProperties#isTrustProxy()}.
	 */
	public boolean trustsProxy() {
		return submissionProperties.isTrustProxy();
	}

	/**
	 * Whether the Space has as many testimonials as its plan allows
	 * (PRD 4.9).
	 */
	@Transactional(readOnly = true)
	public boolean hasReachedLimit(Space space) {
		int limit = planLimits.testimonialLimitPerSpaceFor(
				planLimits.planOf(space.getOwner().getId()));
		return testimonialRepository.countBySpaceId(space.getId()) >= limit;
	}

	/**
	 * Whether the Space rejects changes, and therefore submissions.
	 *
	 * <p>A Space that is read-only after a downgrade no longer takes
	 * submissions either (PRD 4.9).
	 */
	@Transactional(readOnly = true)
	public boolean isReadOnly(Space space) {
		return spaceService.isReadOnly(space.getOwner().getId(), space);
	}

	/**
	 * Whether the hidden field has been filled in.
	 */
	private boolean isHoneypotFilled(SubmissionRequest request) {
		String value = request.companyWebsite();
		return value != null && !value.isBlank();
	}

	/**
	 * Checks the CAPTCHA, if one is configured.
	 *
	 * <p>Skipped entirely when there is no secret, so that development and
	 * test environments work without a Cloudflare account.
	 */
	private void checkCaptcha(SubmissionRequest request, String remoteIp,
			Map<String, String> errors) {

		if (!turnstileProperties.isConfigured()) {
			return;
		}

		if (!turnstileVerifier.verify(request.captchaToken(), remoteIp)) {
			errors.put("captcha", "Please confirm you are human");
		}
	}

	/**
	 * Requires a value when the Space collects the field as required.
	 */
	private void requireIfRequired(Map<String, String> errors, Map<String, Object> formConfig,
			FormField field, String value) {

		if (FormDefinition.isRequired(formConfig, field) && (value == null || value.isBlank())) {
			errors.put(field.getKey(), field.getLabel() + " is required");
		}
	}

	/**
	 * Requires a value to be present.
	 */
	private void requireText(Map<String, String> errors, String field, String value,
			String message) {

		if (value == null || value.isBlank()) {
			errors.put(field, message);
		}
	}

	/**
	 * Checks that a value is not longer than it may be.
	 */
	private void checkLength(Map<String, String> errors, String field, String value,
			int maxLength, String message) {

		if (value != null && value.length() > maxLength) {
			errors.put(field, message);
		}
	}

	/**
	 * Checks that an email looks like an email address.
	 *
	 * <p>A deliberately loose check: one {@code @}, something either side of
	 * it, and no spaces. Anything stricter rejects addresses that work.
	 */
	private void checkEmail(Map<String, String> errors, String email) {
		if (email == null || email.isBlank()) {
			return;
		}

		checkLength(errors, FormField.EMAIL.getKey(), email, EMAIL_MAX_LENGTH,
				"Your email is too long");

		boolean shaped = email.indexOf('@') > 0
				&& email.indexOf('@') == email.lastIndexOf('@')
				&& email.indexOf('@') < email.length() - 1
				&& !email.contains(" ")
				&& !email.contains("\t");

		if (!shaped) {
			errors.put(FormField.EMAIL.getKey(), "Please give a valid email address");
		}
	}

	/**
	 * Checks that a URL is one the platform is willing to publish.
	 *
	 * <p>Only http and https are accepted. The embed puts these links on a
	 * third party's page, so a {@code javascript:} URL would be a way to run
	 * script on somebody else's site (PRD 4.8).
	 */
	private void checkUrl(Map<String, String> errors, String field, String url) {
		if (url == null || url.isBlank()) {
			return;
		}

		checkLength(errors, field, url, URL_MAX_LENGTH, "That link is too long");

		try {
			URI uri = new URI(url.trim());
			String scheme = uri.getScheme();
			if (scheme == null) {
				errors.put(field, "Please give a full link, including https://");
				return;
			}
			String lower = scheme.toLowerCase(Locale.ROOT);
			if (!lower.equals("http") && !lower.equals("https")) {
				errors.put(field, "Please give an http or https link");
				return;
			}
			if (uri.getHost() == null || uri.getHost().isBlank()) {
				errors.put(field, "Please give a full link, including https://");
			}
		} catch (URISyntaxException e) {
			errors.put(field, "Please give a full link, including https://");
		}
	}

	/**
	 * The submitted links, with the blank ones dropped.
	 */
	private List<String> cleanedLinks(List<String> submitted) {
		if (submitted == null) {
			return null;
		}

		List<String> links = new ArrayList<>();
		for (String link : submitted) {
			String trimmed = trimmed(link);
			if (trimmed != null) {
				links.add(trimmed);
			}
		}
		return links;
	}

	/**
	 * The value with its whitespace removed, or null when it is blank.
	 */
	private String trimmed(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * The value with its whitespace removed, or null when it is blank.
	 */
	private String trimToNull(String value) {
		return trimmed(value);
	}

	/**
	 * The IP address as a value the database accepts.
	 *
	 * <p>Null when the address is not one PostgreSQL's {@code inet} type can
	 * hold, so a malformed address is not a failed submission.
	 */
	private InetAddress toInetAddress(String address) {
		if (address == null || address.isBlank()) {
			return null;
		}
		try {
			return InetAddress.getByName(address.trim());
		} catch (UnknownHostException e) {
			return null;
		}
	}
}
