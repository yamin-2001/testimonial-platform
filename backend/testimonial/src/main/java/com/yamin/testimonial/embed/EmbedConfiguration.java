package com.yamin.testimonial.embed;

import java.util.List;
import java.util.Map;

/**
 * A Space's embed configuration, as the owner sees it (PRD 4.8).
 *
 * @param settings the layout, mode, background colour and animation
 * @param publicProfileFields which submitter fields the embed publishes
 */
public record EmbedConfiguration(
		Map<String, Object> settings,
		List<PublicProfileFieldState> publicProfileFields) {
}
