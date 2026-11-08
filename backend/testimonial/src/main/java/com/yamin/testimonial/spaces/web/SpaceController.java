package com.yamin.testimonial.spaces.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.yamin.testimonial.billing.PlanLimits;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceService;
import com.yamin.testimonial.spaces.Theme;
import com.yamin.testimonial.spaces.web.SpaceDtos.CreateSpaceRequest;
import com.yamin.testimonial.spaces.web.SpaceDtos.DashboardSummaryResponse;
import com.yamin.testimonial.spaces.web.SpaceDtos.SpaceResponse;
import com.yamin.testimonial.spaces.web.SpaceDtos.ThemeListResponse;
import com.yamin.testimonial.spaces.web.SpaceDtos.ThemeResponse;
import com.yamin.testimonial.spaces.web.SpaceDtos.UpdateSpaceRequest;
import com.yamin.testimonial.testimonials.TestimonialRepository;

import jakarta.validation.Valid;

/**
 * The Spaces endpoints (PRD 4.2).
 *
 * <p>Every endpoint requires a signed-in user, and every one of them is
 * scoped to that user: a Space belonging to somebody else is a 404.
 */
@RestController
@RequestMapping("/api/spaces")
public class SpaceController {

	private final SpaceService spaceService;

	private final TestimonialRepository testimonialRepository;

	private final PlanLimits planLimits;

	public SpaceController(SpaceService spaceService,
			TestimonialRepository testimonialRepository,
			PlanLimits planLimits) {
		this.spaceService = spaceService;
		this.testimonialRepository = testimonialRepository;
		this.planLimits = planLimits;
	}

	/**
	 * The user's Spaces, oldest first.
	 */
	@GetMapping
	public List<SpaceResponse> list(@AuthenticationPrincipal UUID ownerId) {
		return spaceService.list(ownerId).stream()
				.map(space -> describe(ownerId, space))
				.toList();
	}

	/**
	 * One of the user's Spaces.
	 */
	@GetMapping("/{spaceId}")
	public SpaceResponse get(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID spaceId) {
		return describe(ownerId, spaceService.get(ownerId, spaceId));
	}

	/**
	 * Creates a Space.
	 */
	@PostMapping
	@ResponseStatus(org.springframework.http.HttpStatus.CREATED)
	public SpaceResponse create(@AuthenticationPrincipal UUID ownerId,
			@Valid @RequestBody CreateSpaceRequest request) {

		Space space = spaceService.create(ownerId,
				request.title(),
				request.subtitle(),
				request.slug(),
				request.themeKey(),
				request.ratingEnabled() != null && request.ratingEnabled(),
				request.formConfig());

		return describe(ownerId, space);
	}

	/**
	 * Updates a Space.
	 */
	@PutMapping("/{spaceId}")
	public SpaceResponse update(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID spaceId,
			@Valid @RequestBody UpdateSpaceRequest request) {

		Space space = spaceService.update(ownerId, spaceId,
				request.title(),
				request.subtitle(),
				request.slug(),
				request.themeKey(),
				request.ratingEnabled(),
				request.formConfig());

		return describe(ownerId, space);
	}

	/**
	 * Deletes a Space and everything in it.
	 */
	@DeleteMapping("/{spaceId}")
	@ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID spaceId) {
		spaceService.delete(ownerId, spaceId);
	}

	/**
	 * The themes available to the signed-in user.
	 *
	 * <p>Premium themes are listed but flagged, so the UI can show them
	 * locked with an upgrade prompt (PRD 4.4).
	 */
	@GetMapping("/themes")
	public ThemeListResponse themes(@AuthenticationPrincipal UUID ownerId) {
		boolean premium = planLimits.isPremium(ownerId);

		List<ThemeResponse> themes = Theme.all().stream()
				.map(theme -> new ThemeResponse(theme.getKey(), theme.getLabel(),
						theme.isPremium() && !premium))
				.toList();

		return new ThemeListResponse(themes);
	}

	/**
	 * The dashboard's summary numbers (PRD 4.6).
	 */
	@GetMapping("/dashboard/summary")
	public DashboardSummaryResponse summary(@AuthenticationPrincipal UUID ownerId) {
		long totalSpaces = spaceService.list(ownerId).size();
		long totalTestimonials = testimonialRepository.countBySpaceOwnerId(ownerId);
		long uniqueSubmitters = testimonialRepository.countDistinctSubmittersByOwnerId(ownerId);

		boolean premium = planLimits.isPremium(ownerId);

		return new DashboardSummaryResponse(
				totalSpaces,
				totalTestimonials,
				uniqueSubmitters,
				planLimits.spaceLimitFor(planLimits.planOf(ownerId)),
				premium ? "PREMIUM" : "FREE",
				premium);
	}

	/**
	 * Describes a Space for the API.
	 */
	private SpaceResponse describe(UUID ownerId, Space space) {
		return new SpaceResponse(
				space.getId(),
				space.getTitle(),
				space.getSubtitle(),
				space.getSlug(),
				space.getThemeKey(),
				space.isRatingEnabled(),
				space.getFormConfig(),
				spaceService.isReadOnly(ownerId, space),
				testimonialRepository.countBySpaceId(space.getId()),
				"/s/" + space.getSlug(),
				space.getCreatedAt(),
				space.getUpdatedAt());
	}
}
