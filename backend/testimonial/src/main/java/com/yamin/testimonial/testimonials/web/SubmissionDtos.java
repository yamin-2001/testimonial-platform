package com.yamin.testimonial.testimonials.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The request and response bodies of the public submission endpoints
 * (PRD 4.5).
 */
public final class SubmissionDtos {

	private SubmissionDtos() {
	}

	/**
	 * A testimonial submitted through a Space's public page.
	 *
	 * <p>There are no Bean Validation annotations on this record. Which fields
	 * are required depends on the Space's own configuration, and the Space is
	 * only known once the slug has been looked up. The service therefore
	 * collects every problem and reports them together, rather than the
	 * request body being rejected before the service is reached.
	 */
	public record SubmissionRequest(
			String name,
			String email,
			String companyName,
			String jobTitle,
			String websiteUrl,
			List<String> socialLinks,
			String message,
			Integer rating,
			Boolean consent,
			/** The CAPTCHA token, when a CAPTCHA is configured. */
			String captchaToken,
			/**
			 * The hidden field bots fill in. A human never sees it, so a
			 * value here means the submission is not from a person.
			 */
			String companyWebsite) {
	}

	/**
	 * A Space as the public page sees it.
	 *
	 * <p>Deliberately carries only what a visitor needs: no id, no owner, no
	 * counts, no read-only flag.
	 *
	 * @param title the Space's title
	 * @param subtitle the Space's subtitle
	 * @param slug the slug forming the public URL
	 * @param themeKey the theme the form renders with
	 * @param ratingEnabled whether the form shows a rating control
	 * @param formConfig which optional fields are collected and required
	 * @param accepting whether the Space is currently taking submissions
	 */
	public record PublicSpaceResponse(
			String title,
			String subtitle,
			String slug,
			String themeKey,
			boolean ratingEnabled,
			Map<String, Object> formConfig,
			boolean accepting) {
	}

	/**
	 * The outcome of a submission.
	 *
	 * @param id the new testimonial's id
	 * @param received whether the submission was stored
	 */
	public record SubmissionResponse(UUID id, boolean received) {
	}
}
