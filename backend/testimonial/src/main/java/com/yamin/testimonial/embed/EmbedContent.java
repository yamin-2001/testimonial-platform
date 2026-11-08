package com.yamin.testimonial.embed;

import java.util.List;
import java.util.Map;

/**
 * What an embed renders (PRD 4.8).
 *
 * <p>This is public and unauthenticated, and it is what the embed script
 * fetches. It carries the Space's title and subtitle, its settings, and the
 * testimonials that are on the Wall of Love and visible.
 *
 * @param title the Space's title
 * @param subtitle the Space's subtitle, or null
 * @param settings the layout, mode, background colour and animation
 * @param testimonials the testimonials to show
 */
public record EmbedContent(
		String title,
		String subtitle,
		Map<String, Object> settings,
		List<EmbedTestimonial> testimonials) {
}
