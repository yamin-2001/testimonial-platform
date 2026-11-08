package com.yamin.testimonial.embed;

/**
 * One submitter field, and whether the embed publishes it (PRD 4.8).
 *
 * @param key the field's key
 * @param enabled whether the embed publishes it
 */
public record PublicProfileFieldState(String key, boolean enabled) {
}
