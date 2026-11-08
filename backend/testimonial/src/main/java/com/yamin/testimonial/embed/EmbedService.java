package com.yamin.testimonial.embed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.billing.PlanLimits;
import com.yamin.testimonial.common.exception.ConflictException;
import com.yamin.testimonial.common.exception.NotFoundException;
import com.yamin.testimonial.common.exception.ValidationException;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceRepository;
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.TestimonialRepository;

/**
 * A Space's embed: its configuration and its public content (PRD 4.8).
 *
 * <p>Two things live here, and they are deliberately in one place because
 * they are two answers to the same question -- what an embed shows:
 * <ul>
 *   <li>the <em>configuration</em> the owner sets, which is owner-scoped and
 *       plan-gated;</li>
 *   <li>the <em>content</em>, which is public, unauthenticated and cacheable.</li>
 * </ul>
 *
 * <p>The plan limits are enforced here rather than in the UI (PRD 6.12). The
 * Carousel layout, a background colour and the animation all need Premium; the
 * backend refuses them for a Free user.
 *
 * <p>A Space that already has a premium setting keeps it after a downgrade
 * (PRD 4.8): the check applies when a setting is chosen, not when the
 * configuration is read. That is what makes an existing embed keep rendering
 * after a downgrade while changing to a premium option is blocked.
 */
@Service
public class EmbedService {

	private static final Logger log = LoggerFactory.getLogger(EmbedService.class);

	private final SpaceRepository spaceRepository;

	private final TestimonialRepository testimonialRepository;

	private final SpaceService spaceService;

	private final PlanLimits planLimits;

	public EmbedService(SpaceRepository spaceRepository,
			TestimonialRepository testimonialRepository,
			SpaceService spaceService,
			PlanLimits planLimits) {

		this.spaceRepository = spaceRepository;
		this.testimonialRepository = testimonialRepository;
		this.spaceService = spaceService;
		this.planLimits = planLimits;
	}

	/**
	 * A Space's embed configuration and the fields it publishes.
	 *
	 * @throws NotFoundException if the Space does not exist or is not the
	 *         signed-in user's
	 */
	@Transactional(readOnly = true)
	public EmbedConfiguration configurationOf(UUID ownerId, UUID spaceId) {
		Space space = spaceService.get(ownerId, spaceId);

		return new EmbedConfiguration(
				EmbedSettings.describe(space.getEmbedConfig()),
				describePublicProfileFields(space.getPublicProfileFields()));
	}

	/**
	 * Saves a Space's embed configuration.
	 *
	 * <p>Only the settings that are sent are changed, so a save of the layout
	 * alone leaves the rest alone.
	 *
	 * @param layout the layout, or null to leave it alone
	 * @param mode the mode, or null to leave it alone
	 * @param backgroundColor the background colour, or null to leave it alone;
	 *        an empty string switches it off
	 * @param animation whether the animation is on, or null to leave it alone
	 * @param publicProfileFields the fields to publish, or null to leave them
	 *        alone
	 * @throws NotFoundException if the Space does not exist or is not the
	 *         signed-in user's
	 * @throws ConflictException if the Space is read-only (PRD 4.9)
	 * @throws ValidationException if a premium setting is asked for on the Free
	 *         plan, or a value is not acceptable
	 */
	@Transactional
	public EmbedConfiguration save(UUID ownerId, UUID spaceId, String layout, String mode,
			String backgroundColor, Boolean animation, List<String> publicProfileFields) {

		Space space = spaceService.get(ownerId, spaceId);
		assertMutable(ownerId, space);

		boolean premium = planLimits.isPremium(ownerId);
		Map<String, Object> config = new LinkedHashMap<>(space.getEmbedConfig());

		if (layout != null) {
			EmbedLayout resolved = EmbedLayout.byKey(layout)
					.orElseThrow(() -> new ValidationException("layout", "Unknown layout"));

			if (resolved.isPremium() && !premium) {
				throw new ValidationException("layout", "The Carousel layout is part of the Premium plan");
			}

			config.put(EmbedSettings.LAYOUT, resolved.getKey());
		}

		if (mode != null) {
			EmbedMode resolved = EmbedMode.byKey(mode)
					.orElseThrow(() -> new ValidationException("mode", "Unknown mode"));

			config.put(EmbedSettings.MODE, resolved.getKey());
		}

		if (backgroundColor != null) {
			String trimmed = backgroundColor.trim();

			if (trimmed.isEmpty()) {
				config.put(EmbedSettings.BACKGROUND_COLOR, "");
			} else {
				if (!premium) {
					throw new ValidationException("backgroundColor",
							"A background colour is part of the Premium plan");
				}
				if (!EmbedSettings.isHexColour(trimmed)) {
					throw new ValidationException("backgroundColor",
							"Please give a colour like #1f2937");
				}
				config.put(EmbedSettings.BACKGROUND_COLOR, trimmed);
			}
		}

		if (animation != null) {
			if (animation && !premium) {
				throw new ValidationException("animation", "Animation is part of the Premium plan");
			}
			config.put(EmbedSettings.ANIMATION, animation);
		}

		if (publicProfileFields != null) {
			space.setPublicProfileFields(normalisePublicProfileFields(publicProfileFields));
		}

		space.setEmbedConfig(config);
		Space saved = spaceRepository.save(space);

		log.info("Saved the embed configuration for Space {}", saved.getSlug());

		return new EmbedConfiguration(
				EmbedSettings.describe(saved.getEmbedConfig()),
				describePublicProfileFields(saved.getPublicProfileFields()));
	}

	/**
	 * The content an embed shows: the testimonials that are on the Wall of Love
	 * and visible (PRD 4.8, 6.10).
	 *
	 * <p>Hidden and deleted testimonials are excluded by the query itself, so
	 * an embed reflects a hide, a removal or a delete without the owner
	 * regenerating the snippet.
	 *
	 * @throws NotFoundException if no Space has that slug
	 */
	@Transactional(readOnly = true)
	public EmbedContent contentBySlug(String slug) {
		Space space = spaceRepository.findBySlug(slug)
				.orElseThrow(() -> new NotFoundException("Space not found"));

		List<Testimonial> testimonials =
				testimonialRepository.findEmbeddableBySpaceId(space.getId());

		Set<String> published = publishedFields(space.getPublicProfileFields());

		List<EmbedTestimonial> content = testimonials.stream()
				.map(testimonial -> describe(testimonial, published))
				.toList();

		return new EmbedContent(
				space.getTitle(),
				space.getSubtitle(),
				EmbedSettings.describe(space.getEmbedConfig()),
				content);
	}

	/**
	 * Whether the Space is read-only and so rejects the change (PRD 4.9).
	 */
	private void assertMutable(UUID ownerId, Space space) {
		if (spaceService.isReadOnly(ownerId, space)) {
			throw new ConflictException(
					"This Space is read-only because you are on the Free plan with more Spaces than the limit allows");
		}
	}

	/**
	 * The fields the embed publishes, as the API presents them.
	 *
	 * <p>Every field is listed, with a flag saying whether it is on. The
	 * frontend shows them all and marks the ones that are locked.
	 */
	private List<PublicProfileFieldState> describePublicProfileFields(List<String> stored) {
		Set<String> enabled = publishedFields(stored);

		return PublicProfileField.all().stream()
				.map(field -> new PublicProfileFieldState(field.getKey(), enabled.contains(field.getKey())))
				.toList();
	}

	/**
	 * The stored fields, with the unknown ones dropped.
	 *
	 * <p>The list is a set rather than a list, so a field cannot be published
	 * twice and the order is the order the fields are declared in.
	 */
	private List<String> normalisePublicProfileFields(List<String> submitted) {
		Set<String> known = new LinkedHashSet<>();

		for (PublicProfileField field : PublicProfileField.all()) {
			if (submitted.contains(field.getKey())) {
				known.add(field.getKey());
			}
		}

		return List.copyOf(known);
	}

	/**
	 * The fields the embed publishes.
	 */
	private Set<String> publishedFields(List<String> stored) {
		Set<String> published = new LinkedHashSet<>();

		if (stored == null) {
			return published;
		}

		for (PublicProfileField field : PublicProfileField.all()) {
			if (stored.contains(field.getKey())) {
				published.add(field.getKey());
			}
		}

		return published;
	}

	/**
	 * Describes one testimonial for the embed.
	 *
	 * <p>The email is not here and never will be: it is the owner's business,
	 * not the reader's (PRD 4.8).
	 */
	private EmbedTestimonial describe(Testimonial testimonial, Set<String> published) {
		List<String> socialLinks = new ArrayList<>();
		if (published.contains(PublicProfileField.SOCIAL_LINKS.getKey())
				&& testimonial.getSocialLinks() != null) {
			socialLinks.addAll(testimonial.getSocialLinks());
		}

		return new EmbedTestimonial(
				testimonial.getId(),
				testimonial.getName(),
				testimonial.getMessage(),
				testimonial.getRating() == null ? null : testimonial.getRating().intValue(),
				published.contains(PublicProfileField.COMPANY_NAME.getKey())
						? testimonial.getCompanyName()
						: null,
				published.contains(PublicProfileField.JOB_TITLE.getKey())
						? testimonial.getJobTitle()
						: null,
				published.contains(PublicProfileField.WEBSITE_URL.getKey())
						? testimonial.getWebsiteUrl()
						: null,
				List.copyOf(socialLinks));
	}
}
