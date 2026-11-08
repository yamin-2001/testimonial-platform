package com.yamin.testimonial.spaces.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The request and response bodies of the Spaces endpoints.
 */
public final class SpaceDtos {

	private SpaceDtos() {
	}

	/**
	 * A request to create a Space.
	 *
	 * <p>Only the title is required; the rest have sensible defaults so that
	 * the simplest possible request works.
	 */
	public record CreateSpaceRequest(
			@NotBlank(message = "Title is required")
			@Size(max = 200, message = "Title must be at most 200 characters")
			String title,

			@Size(max = 500, message = "Subtitle must be at most 500 characters")
			String subtitle,

			@Size(max = 120, message = "URL must be at most 120 characters")
			String slug,

			@Size(max = 50, message = "Unknown theme")
			String themeKey,

			Boolean ratingEnabled,

			Map<String, Object> formConfig) {
	}

	/**
	 * A request to update a Space.
	 *
	 * <p>Every field is optional: only what is sent is changed.
	 */
	public record UpdateSpaceRequest(
			@Size(max = 200, message = "Title must be at most 200 characters")
			String title,

			@Size(max = 500, message = "Subtitle must be at most 500 characters")
			String subtitle,

			@Size(max = 120, message = "URL must be at most 120 characters")
			String slug,

			@Size(max = 50, message = "Unknown theme")
			String themeKey,

			Boolean ratingEnabled,

			Map<String, Object> formConfig) {
	}

	/**
	 * A Space as returned by the API.
	 *
	 * @param id the Space's id
	 * @param title the Space's title
	 * @param subtitle the Space's subtitle
	 * @param slug the slug forming the public URL
	 * @param themeKey the theme the public form renders with
	 * @param ratingEnabled whether the public form shows a rating control
	 * @param formConfig which optional fields are collected and required
	 * @param readOnly whether the Space rejects changes (PRD 4.9)
	 * @param testimonialCount how many testimonials the Space holds
	 * @param publicUrl the Space's public URL
	 * @param createdAt when the Space was created
	 * @param updatedAt when the Space was last changed
	 */
	public record SpaceResponse(
			UUID id,
			String title,
			String subtitle,
			String slug,
			String themeKey,
			boolean ratingEnabled,
			Map<String, Object> formConfig,
			boolean readOnly,
			long testimonialCount,
			String publicUrl,
			Instant createdAt,
			Instant updatedAt) {
	}

	/**
	 * A theme as shown in the picker.
	 *
	 * @param key the value stored on the Space
	 * @param label the name shown to the user
	 * @param premium whether the theme requires Premium
	 */
	public record ThemeResponse(String key, String label, boolean premium) {
	}

	/**
	 * The themes available to the signed-in user.
	 */
	public record ThemeListResponse(List<ThemeResponse> themes) {
	}

	/**
	 * The dashboard's summary numbers (PRD 4.6).
	 *
	 * @param totalSpaces how many Spaces the user owns
	 * @param totalTestimonials how many testimonials across those Spaces
	 * @param uniqueSubmitters how many distinct emails submitted
	 * @param spaceLimit how many Spaces the user may own
	 * @param plan the user's plan
	 * @param premium whether the user has Premium access
	 */
	public record DashboardSummaryResponse(
			long totalSpaces,
			long totalTestimonials,
			long uniqueSubmitters,
			int spaceLimit,
			String plan,
			boolean premium) {
	}
}
