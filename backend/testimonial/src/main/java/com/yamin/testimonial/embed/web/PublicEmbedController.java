package com.yamin.testimonial.embed.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yamin.testimonial.embed.EmbedContent;
import com.yamin.testimonial.embed.EmbedService;

/**
 * The public endpoint an embed fetches its content from (PRD 4.8).
 *
 * <p>It needs no session, accepts no body and changes nothing: it is a
 * read-only GET under {@code /api/public/}, which is already permitted
 * without authentication.
 *
 * <p>The response is cacheable, so a host page can serve the widget from a
 * CDN or a browser cache rather than asking the backend on every view. The
 * cache window is short, because a testimonial the owner hides or removes
 * should stop appearing promptly (PRD 4.8).
 */
@RestController
@RequestMapping("/api/public/embed")
public class PublicEmbedController {

	/** How long a client may cache the content. */
	private static final Duration CACHE_MAX_AGE = Duration.ofMinutes(5);

	private final EmbedService embedService;

	public PublicEmbedController(EmbedService embedService) {
		this.embedService = embedService;
	}

	/**
	 * The content an embed shows.
	 *
	 * <p>An unknown slug is a 404. The embed script treats that as "nothing to
	 * show" and renders nothing, so a deleted Space does not error the host
	 * page (PRD 4.8).
	 */
	@GetMapping("/{slug}")
	public ResponseEntity<EmbedContent> get(@PathVariable String slug) {
		EmbedContent content = embedService.contentBySlug(slug);

		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePublic())
				.body(content);
	}
}
