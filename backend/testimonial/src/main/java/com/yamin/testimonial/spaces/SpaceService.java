package com.yamin.testimonial.spaces;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.billing.Plan;
import com.yamin.testimonial.billing.PlanLimits;
import com.yamin.testimonial.common.exception.ConflictException;
import com.yamin.testimonial.common.exception.NotFoundException;
import com.yamin.testimonial.common.exception.ValidationException;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * Managing Spaces (PRD 4.2, 4.3, 4.4).
 *
 * <p>Every operation is scoped to the signed-in user, so one user can never
 * read or modify another's Spaces (PRD 6.7). A Space that belongs to someone
 * else is answered with 404 rather than 403, so the response does not reveal
 * that it exists (PRD 4.2, 8).
 */
@Service
public class SpaceService {

	private static final Logger log = LoggerFactory.getLogger(SpaceService.class);

	/** The longest a slug may be. */
	private static final int SLUG_MAX_LENGTH = 120;

	private final SpaceRepository spaceRepository;

	private final TestimonialRepository testimonialRepository;

	private final AppUserRepository appUserRepository;

	private final PlanLimits planLimits;

	public SpaceService(SpaceRepository spaceRepository,
			TestimonialRepository testimonialRepository,
			AppUserRepository appUserRepository,
			PlanLimits planLimits) {
		this.spaceRepository = spaceRepository;
		this.testimonialRepository = testimonialRepository;
		this.appUserRepository = appUserRepository;
		this.planLimits = planLimits;
	}

	/**
	 * The user's Spaces, oldest first.
	 */
	@Transactional(readOnly = true)
	public List<Space> list(UUID ownerId) {
		return spaceRepository.findByOwnerIdOrderBySeqAsc(ownerId);
	}

	/**
	 * One of the user's Spaces.
	 *
	 * @throws NotFoundException if the Space does not exist or is not theirs
	 */
	@Transactional(readOnly = true)
	public Space get(UUID ownerId, UUID spaceId) {
		return spaceRepository.findByIdAndOwnerId(spaceId, ownerId)
				.orElseThrow(() -> new NotFoundException("Space not found"));
	}

	/**
	 * Creates a Space.
	 *
	 * <p>The slug is generated from the title unless one is given, must be
	 * unique across the platform, and the user must be within their plan's
	 * Space limit (PRD 4.2).
	 */
	@Transactional
	public Space create(UUID ownerId, String title, String subtitle, String slug,
			String themeKey, boolean ratingEnabled, Map<String, Object> formConfig) {

		AppUser owner = appUserRepository.findById(ownerId)
				.orElseThrow(() -> new NotFoundException("Account not found"));

		if (!owner.isEmailVerified()) {
			// An unverified account may sign in and look around, but may not
			// create Spaces (PRD 4.1).
			throw new ValidationException("emailVerified",
					"Confirm your email address before creating a Space");
		}

		enforceSpaceLimit(ownerId);

		String resolvedSlug = resolveSlug(slug, title);
		assertSlugAvailable(resolvedSlug);

		Space space = new Space();
		space.setOwner(owner);
		space.setTitle(title.trim());
		space.setSubtitle(blankToNull(subtitle));
		space.setSlug(resolvedSlug);
		space.setThemeKey(resolveTheme(themeKey, ownerId).getKey());
		space.setRatingEnabled(ratingEnabled);
		space.setFormConfig(normaliseFormConfig(formConfig));
		space.setEmbedConfig(com.yamin.testimonial.embed.EmbedSettings.defaults());
		space.setPublicProfileFields(new java.util.ArrayList<>());

		Space saved = spaceRepository.save(space);
		log.info("Created Space {} for user {}", saved.getSlug(), ownerId);
		return saved;
	}

	/**
	 * Updates a Space.
	 *
	 * <p>The slug is locked once the Space has received its first submission
	 * (PRD 4.2). A Space that is read-only after a downgrade rejects every
	 * change (PRD 4.9).
	 */
	@Transactional
	public Space update(UUID ownerId, UUID spaceId, String title, String subtitle, String slug,
			String themeKey, Boolean ratingEnabled, Map<String, Object> formConfig) {

		Space space = get(ownerId, spaceId);
		assertMutable(ownerId, space);

		if (title != null && !title.isBlank()) {
			space.setTitle(title.trim());
		}
		if (subtitle != null) {
			space.setSubtitle(blankToNull(subtitle));
		}
		if (slug != null && !slug.isBlank()) {
			applySlug(space, slug.trim());
		}
		if (themeKey != null && !themeKey.isBlank()) {
			space.setThemeKey(resolveTheme(themeKey, ownerId).getKey());
		}
		if (ratingEnabled != null) {
			space.setRatingEnabled(ratingEnabled);
		}
		if (formConfig != null) {
			space.setFormConfig(normaliseFormConfig(formConfig));
		}

		return spaceRepository.save(space);
	}

	/**
	 * Deletes a Space and everything in it.
	 *
	 * <p>The deletion is permanent: the Space, its testimonials and its
	 * public URL all go (PRD 4.2).
	 */
	@Transactional
	public void delete(UUID ownerId, UUID spaceId) {
		Space space = get(ownerId, spaceId);
		assertMutable(ownerId, space);

		// The testimonials cascade with the Space through the foreign key.
		spaceRepository.delete(space);
		log.info("Deleted Space {} for user {}", space.getSlug(), ownerId);
	}

	/**
	 * Whether the user may change this Space.
	 *
	 * <p>A Space beyond the Free limit after a downgrade is read-only
	 * (OPEN-B): the oldest Spaces stay editable and the rest do not.
	 */
	@Transactional(readOnly = true)
	public boolean isReadOnly(UUID ownerId, Space space) {
		if (planLimits.isPremium(ownerId)) {
			return false;
		}

		List<Space> spaces = spaceRepository.findByOwnerIdOrderBySeqAsc(ownerId);
		if (spaces.size() <= PlanLimits.FREE_SPACE_LIMIT) {
			return false;
		}

		int position = -1;
		for (int i = 0; i < spaces.size(); i++) {
			if (spaces.get(i).getId().equals(space.getId())) {
				position = i;
				break;
			}
		}
		if (position < 0) {
			return false;
		}

		// The oldest three by creation order stay editable; the rest do not.
		return position >= PlanLimits.FREE_SPACE_LIMIT;
	}

	/**
	 * Rejects a change to a Space that is read-only (PRD 4.9).
	 */
	private void assertMutable(UUID ownerId, Space space) {
		if (isReadOnly(ownerId, space)) {
			throw new ConflictException(
					"This Space is read-only because you are on the Free plan with more Spaces than the limit allows");
		}
	}

	/**
	 * Rejects a Space creation that would exceed the plan's limit (PRD 4.2).
	 */
	private void enforceSpaceLimit(UUID ownerId) {
		Plan plan = planLimits.planOf(ownerId);
		int limit = planLimits.spaceLimitFor(plan);
		long current = spaceRepository.countByOwnerId(ownerId);

		if (current >= limit) {
			throw new PlanLimitException(limit, plan);
		}
	}

	/**
	 * Resolves the slug to use.
	 *
	 * <p>One is generated from the title when the user did not supply one
	 * (PRD 4.2).
	 */
	private String resolveSlug(String slug, String title) {
		if (slug != null && !slug.isBlank()) {
			return normaliseSlug(slug);
		}
		return normaliseSlug(title);
	}

	/**
	 * Turns text into a URL slug.
	 *
	 * <p>Accents are folded to their ASCII letter, anything that is not a
	 * letter or a digit becomes a hyphen, and runs of hyphens collapse.
	 */
	private String normaliseSlug(String text) {
		String normalised = Normalizer.normalize(text, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+|-+$", "");

		if (normalised.isEmpty()) {
			throw new ValidationException("slug", "The title must contain at least one letter or digit");
		}
		if (normalised.length() > SLUG_MAX_LENGTH) {
			normalised = normalised.substring(0, SLUG_MAX_LENGTH).replaceAll("-+$", "");
		}
		return normalised;
	}

	/**
	 * Rejects a slug that is already taken.
	 *
	 * <p>Slugs are unique across the platform, not just per user, because
	 * they form the public URL (PRD 4.2).
	 */
	private void assertSlugAvailable(String slug) {
		if (spaceRepository.findBySlug(slug).isPresent()) {
			throw new ValidationException("slug", "That URL is already taken");
		}
	}

	/**
	 * Applies a new slug, honouring the lock.
	 *
	 * <p>Once the Space has received its first submission the slug is locked,
	 * because changing it would break the public URL people have already
	 * shared (PRD 4.2).
	 */
	private void applySlug(Space space, String slug) {
		String normalised = normaliseSlug(slug);

		if (normalised.equals(space.getSlug())) {
			return;
		}

		boolean locked = spaceRepository.hasAnyTestimonials(space.getId());
		if (locked) {
			throw new ConflictException(
					"The URL cannot be changed because this Space has already received a testimonial");
		}

		assertSlugAvailable(normalised);
		space.setSlug(normalised);
	}

	/**
	 * Resolves the theme, rejecting a premium one for a Free user (PRD 4.4).
	 *
	 * <p>When no theme is given the first basic one is used, so that the
	 * simplest possible request -- a title and nothing else -- works.
	 */
	private Theme resolveTheme(String themeKey, UUID ownerId) {
		Theme theme;
		if (themeKey == null || themeKey.isBlank()) {
			theme = Theme.basicThemes().get(0);
		} else {
			theme = Theme.byKey(themeKey)
					.orElseThrow(() -> new ValidationException("themeKey", "Unknown theme"));
		}

		if (theme.isPremium() && !planLimits.isPremium(ownerId)) {
			throw new ValidationException("themeKey",
					"That theme is part of the Premium plan");
		}

		return theme;
	}

	/**
	 * Normalises the submitted form configuration.
	 *
	 * <p>Only the configurable fields are kept, and each one is reduced to an
	 * enabled and a required flag. The fixed fields are not in the
	 * configuration at all -- they are always collected (PRD 4.3).
	 */
	private Map<String, Object> normaliseFormConfig(Map<String, Object> submitted) {
		Map<String, Object> config = new LinkedHashMap<>();

		for (FormField field : FormField.configurableFields()) {
			Object raw = submitted == null ? null : submitted.get(field.getKey());
			boolean enabled = true;
			boolean required = false;

			if (raw instanceof Map<?, ?> settings) {
				enabled = asBoolean(settings.get("enabled"), true);
				required = asBoolean(settings.get("required"), false);
			}

			// A field that is off cannot be required.
			if (!enabled) {
				required = false;
			}

			Map<String, Object> settings = new LinkedHashMap<>();
			settings.put("enabled", enabled);
			settings.put("required", required);
			config.put(field.getKey(), settings);
		}

		return FormDefinition.describe(config);
	}

	private boolean asBoolean(Object value, boolean fallback) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof String text) {
			return Boolean.parseBoolean(text);
		}
		return fallback;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
