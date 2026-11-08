package com.yamin.testimonial.testimonials.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The request and response bodies of the inbox endpoints (PRD 4.7).
 */
public final class InboxDtos {

	private InboxDtos() {
	}

	/**
	 * A request to edit a testimonial.
	 *
	 * <p>Every field is optional: only what is sent is changed, so an edit of
	 * the text leaves the name and everything else alone.
	 *
	 * @param name the submitter's name
	 * @param email the submitter's email address
	 * @param message the testimonial text
	 * @param rating the rating, 1 to 5
	 */
	public record UpdateTestimonialRequest(
			String name,
			String email,
			String message,
			Integer rating) {
	}

	/**
	 * A request to set one of the owner's flags on a testimonial.
	 *
	 * @param value the new value of the flag
	 */
	public record SetFlagRequest(Boolean value) {
	}

	/**
	 * A testimonial as shown in the owner's inbox (PRD 4.7).
	 *
	 * <p>The submitter's email is included: the inbox is the owner's own view
	 * of who wrote to them, and the email is never exposed publicly (PRD 4.8).
	 *
	 * @param id the testimonial's id
	 * @param spaceId the Space it belongs to
	 * @param name the submitter's name
	 * @param email the submitter's email address
	 * @param message the testimonial text
	 * @param rating the rating, or null when there is none
	 * @param companyName the submitter's company, when collected
	 * @param jobTitle the submitter's job title, when collected
	 * @param websiteUrl the submitter's website, when collected
	 * @param socialLinks the submitter's social links, when collected
	 * @param consentGiven whether the submitter ticked the consent box
	 * @param favorite whether the owner marked it a favourite
	 * @param wallOfLove whether it is on the Wall of Love
	 * @param hidden whether the owner hid it from the public pages
	 * @param createdAt when it was submitted
	 * @param updatedAt when it was last changed
	 */
	public record TestimonialResponse(
			UUID id,
			UUID spaceId,
			String name,
			String email,
			String message,
			Integer rating,
			String companyName,
			String jobTitle,
			String websiteUrl,
			List<String> socialLinks,
			boolean consentGiven,
			boolean favorite,
			boolean wallOfLove,
			boolean hidden,
			Instant createdAt,
			Instant updatedAt) {
	}

	/**
	 * One page of the inbox (PRD 4.7).
	 *
	 * @param content the testimonials on this page
	 * @param page the page number, counting from zero
	 * @param size how many testimonials a page holds
	 * @param totalElements how many testimonials match the filter
	 * @param totalPages how many pages there are
	 */
	public record InboxPageResponse(
			List<TestimonialResponse> content,
			int page,
			int size,
			long totalElements,
			int totalPages) {
	}
}
