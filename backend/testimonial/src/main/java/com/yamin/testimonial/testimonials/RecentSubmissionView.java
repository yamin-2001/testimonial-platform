package com.yamin.testimonial.testimonials;

import java.time.Instant;
import java.util.UUID;

/**
 * A testimonial as the dashboard's recent-submissions list shows it.
 *
 * <p>The Space's title and slug come along with the testimonial, because the
 * list shows which Space each submission came from and the reference to it
 * is lazy: {@code open-in-view} is off, so it cannot be read once the
 * transaction has ended.
 *
 * @param id the testimonial's id
 * @param name the submitter's name
 * @param message the testimonial text
 * @param rating the rating, if one was given
 * @param spaceId the Space's id
 * @param spaceTitle the Space it was submitted to
 * @param spaceSlug that Space's slug
 * @param submittedAt when it was submitted
 */
public record RecentSubmissionView(
		UUID id,
		String name,
		String message,
		Short rating,
		UUID spaceId,
		String spaceTitle,
		String spaceSlug,
		Instant submittedAt) {
}
