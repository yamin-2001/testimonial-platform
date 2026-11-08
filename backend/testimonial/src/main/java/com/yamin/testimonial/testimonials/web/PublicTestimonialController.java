package com.yamin.testimonial.testimonials.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.yamin.testimonial.spaces.FormDefinition;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.testimonials.Testimonial;
import com.yamin.testimonial.testimonials.TestimonialService;
import com.yamin.testimonial.testimonials.web.SubmissionDtos.PublicSpaceResponse;
import com.yamin.testimonial.testimonials.web.SubmissionDtos.SubmissionRequest;
import com.yamin.testimonial.testimonials.web.SubmissionDtos.SubmissionResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The public endpoints of a Space's submission page (PRD 4.5).
 *
 * <p>Neither endpoint needs a session: a visitor fills in the form without
 * signing in (PRD 6.4).
 *
 * <p>They live under {@code /api/public/} rather than under {@code /api/spaces/}
 * because {@code SpaceController} already maps {@code /api/spaces/{spaceId}}
 * with a UUID path variable. A slug under the same prefix would be ambiguous.
 */
@RestController
@RequestMapping("/api/public/spaces/{slug}")
public class PublicTestimonialController {

	private final TestimonialService testimonialService;

	public PublicTestimonialController(TestimonialService testimonialService) {
		this.testimonialService = testimonialService;
	}

	/**
	 * The Space behind a slug, as the public page needs it.
	 *
	 * <p>Carries only what a visitor needs, and says whether the Space is
	 * currently taking submissions so the page can explain itself instead of
	 * letting the visitor fill in a form that will be refused.
	 */
	@GetMapping
	public PublicSpaceResponse get(@PathVariable String slug) {
		Space space = testimonialService.findBySlug(slug);

		return new PublicSpaceResponse(
				space.getTitle(),
				space.getSubtitle(),
				space.getSlug(),
				space.getThemeKey(),
				space.isRatingEnabled(),
				FormDefinition.describe(space.getFormConfig()),
				testimonialService.isAccepting(space));
	}

	/**
	 * Receives a testimonial.
	 *
	 * <p>A submission that filled the honeypot is answered with 201 and
	 * nothing stored, so that a bot learns nothing from the response.
	 */
	@PostMapping("/testimonials")
	@ResponseStatus(HttpStatus.CREATED)
	public SubmissionResponse submit(@PathVariable String slug,
			@RequestBody SubmissionRequest request,
			HttpServletRequest httpRequest) {

		Testimonial saved = testimonialService.submit(slug, request,
				remoteIp(httpRequest), httpRequest.getHeader("User-Agent"));

		return new SubmissionResponse(saved == null ? null : saved.getId(), true);
	}

	/**
	 * The submitter's IP address.
	 *
	 * <p>The {@code X-Forwarded-For} header is only believed when a proxy is
	 * known to be in front. Without one, the header is whatever the client
	 * sent, and using it as a rate-limit key would let a caller choose their
	 * own key.
	 */
	private String remoteIp(HttpServletRequest request) {
		if (testimonialService.trustsProxy()) {
			String forwarded = request.getHeader("X-Forwarded-For");
			if (forwarded != null && !forwarded.isBlank()) {
				// The leftmost entry is the original client.
				int comma = forwarded.indexOf(',');
				String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
				return first.trim();
			}
		}
		return request.getRemoteAddr();
	}
}
