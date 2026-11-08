package com.yamin.testimonial.embed.web;

import java.util.List;

/**
 * The request and response bodies of the embed endpoints (PRD 4.8).
 */
public final class EmbedDtos {

	private EmbedDtos() {
	}

	/**
	 * A request to save a Space's embed configuration.
	 *
	 * <p>Every field is optional: only what is sent is changed.
	 */
	public record UpdateEmbedRequest(
			String layout,
			String mode,
			String backgroundColor,
			Boolean animation,
			List<String> publicProfileFields) {
	}
}
