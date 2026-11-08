package com.yamin.testimonial.testimonials.web;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.yamin.testimonial.testimonials.InboxService;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.web.InboxDtos.InboxPageResponse;
import com.yamin.testimonial.testimonials.web.InboxDtos.SetFlagRequest;
import com.yamin.testimonial.testimonials.web.InboxDtos.TestimonialResponse;
import com.yamin.testimonial.testimonials.web.InboxDtos.UpdateTestimonialRequest;

/**
 * The inbox endpoints (PRD 4.7).
 *
 * <p>Every endpoint requires a signed-in user, and every one of them is
 * scoped to that user: a testimonial belonging to somebody else's Space is a
 * 404.
 *
 * <p>The listing is under the Space's own path, so the owner always acts
 * within one Space and never has to say which Space a testimonial belongs to.
 */
@RestController
@RequestMapping("/api/spaces/{spaceId}/testimonials")
public class InboxController {

	/** How many testimonials a page holds when the caller does not say. */
	private static final int DEFAULT_PAGE_SIZE = 20;

	/** The most testimonials a page can hold. */
	private static final int MAX_PAGE_SIZE = 100;

	private final InboxService inboxService;

	public InboxController(InboxService inboxService) {
		this.inboxService = inboxService;
	}

	/**
	 * The Space's inbox.
	 *
	 * <p>Favourites first, then newest first. The filters narrow the list:
	 * {@code favorite}, {@code wallOfLove}, {@code hidden} and {@code rating}.
	 * A filter that is not given is not applied.
	 */
	@GetMapping
	public InboxPageResponse list(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID spaceId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Boolean favorite,
			@RequestParam(required = false) Boolean wallOfLove,
			@RequestParam(required = false) Boolean hidden,
			@RequestParam(required = false) Integer rating,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {

		Short ratingFilter = rating == null ? null : (short) rating.intValue();

		Page<Testimonial> result = inboxService.list(ownerId, spaceId, search, favorite, wallOfLove,
				hidden, ratingFilter, pageRequest(page, size));

		return new InboxPageResponse(
				result.getContent().stream().map(InboxController::describe).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	/**
	 * One of the owner's testimonials.
	 */
	@GetMapping("/{testimonialId}")
	public TestimonialResponse get(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID testimonialId) {

		return describe(inboxService.get(ownerId, testimonialId));
	}

	/**
	 * Marks a testimonial as a favourite, or unmarks it.
	 */
	@PutMapping("/{testimonialId}/favorite")
	public TestimonialResponse setFavorite(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID testimonialId,
			@RequestBody SetFlagRequest request) {

		return describe(inboxService.setFavorite(ownerId, testimonialId, value(request)));
	}

	/**
	 * Adds a testimonial to the Wall of Love, or takes it off.
	 */
	@PutMapping("/{testimonialId}/wall-of-love")
	public TestimonialResponse setWallOfLove(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID testimonialId,
			@RequestBody SetFlagRequest request) {

		return describe(inboxService.setWallOfLove(ownerId, testimonialId, value(request)));
	}

	/**
	 * Hides a testimonial from the public pages, or shows it again.
	 */
	@PutMapping("/{testimonialId}/hidden")
	public TestimonialResponse setHidden(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID testimonialId,
			@RequestBody SetFlagRequest request) {

		return describe(inboxService.setHidden(ownerId, testimonialId, value(request)));
	}

	/**
	 * Edits a testimonial's details.
	 */
	@PatchMapping("/{testimonialId}")
	public TestimonialResponse edit(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID testimonialId,
			@RequestBody UpdateTestimonialRequest request) {

		return describe(inboxService.edit(ownerId, testimonialId,
				request.name(), request.email(), request.message(), request.rating()));
	}

	/**
	 * Deletes a testimonial for good.
	 */
	@DeleteMapping("/{testimonialId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID testimonialId) {

		inboxService.delete(ownerId, testimonialId);
	}

	/**
	 * The value of a flag from the request.
	 *
	 * <p>A request that does not say is taken as "yes", so that a body of
	 * {@code {}} turns the flag on rather than silently doing nothing.
	 */
	private boolean value(SetFlagRequest request) {
		return request == null || request.value() == null || request.value();
	}

	/**
	 * The page to read.
	 *
	 * <p>The size is clamped so that a caller cannot ask for the whole table
	 * in one request, and the page number cannot be negative.
	 */
	private PageRequest pageRequest(int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

		// The sort is fixed: favourites first, then newest first (PRD 4.7).
		// The repository's own ordering applies, so the page is unsorted here.
		return PageRequest.of(safePage, safeSize, Sort.unsorted());
	}

	/**
	 * Describes a testimonial for the API.
	 */
	private static TestimonialResponse describe(Testimonial testimonial) {
		return new TestimonialResponse(
				testimonial.getId(),
				testimonial.getSpace().getId(),
				testimonial.getName(),
				testimonial.getEmail(),
				testimonial.getMessage(),
				testimonial.getRating() == null ? null : testimonial.getRating().intValue(),
				testimonial.getCompanyName(),
				testimonial.getJobTitle(),
				testimonial.getWebsiteUrl(),
				testimonial.getSocialLinks() == null ? List.of() : testimonial.getSocialLinks(),
				testimonial.isConsentGiven(),
				testimonial.isFavorite(),
				testimonial.isWallOfLove(),
				testimonial.isHidden(),
				testimonial.getCreatedAt(),
				testimonial.getUpdatedAt());
	}
}
