package com.yamin.testimonial.embed.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yamin.testimonial.embed.EmbedConfiguration;
import com.yamin.testimonial.embed.EmbedService;
import com.yamin.testimonial.embed.web.EmbedDtos.UpdateEmbedRequest;

/**
 * The owner's endpoints for a Space's embed configuration (PRD 4.8).
 *
 * <p>Every endpoint requires a signed-in user, and every one of them is
 * scoped to that user: a Space belonging to somebody else is a 404.
 */
@RestController
@RequestMapping("/api/spaces/{spaceId}/embed")
public class EmbedController {

	private final EmbedService embedService;

	public EmbedController(EmbedService embedService) {
		this.embedService = embedService;
	}

	/**
	 * The Space's embed configuration.
	 */
	@GetMapping
	public EmbedConfiguration get(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID spaceId) {

		return embedService.configurationOf(ownerId, spaceId);
	}

	/**
	 * Saves the Space's embed configuration.
	 *
	 * <p>Only what is sent is changed. The premium settings are rejected by the
	 * backend for a Free user, whatever the UI showed them as (PRD 6.12).
	 */
	@PutMapping
	public EmbedConfiguration update(@AuthenticationPrincipal UUID ownerId,
			@PathVariable UUID spaceId,
			@RequestBody UpdateEmbedRequest request) {

		return embedService.save(ownerId, spaceId,
				request.layout(),
				request.mode(),
				request.backgroundColor(),
				request.animation(),
				request.publicProfileFields());
	}
}
