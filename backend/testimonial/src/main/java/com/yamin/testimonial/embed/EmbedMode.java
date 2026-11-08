package com.yamin.testimonial.embed;

import java.util.Optional;

/**
 * Whether the embed is light or dark (PRD 4.8).
 *
 * <p>Both modes are available on every plan.
 */
public enum EmbedMode {

	/** A light background with dark text. */
	LIGHT("light", "Light"),

	/** A dark background with light text. */
	DARK("dark", "Dark");

	private final String key;

	private final String label;

	EmbedMode(String key, String label) {
		this.key = key;
		this.label = label;
	}

	/** The value stored in {@code spaces.embed_config}. */
	public String getKey() {
		return key;
	}

	/** The name shown to the user. */
	public String getLabel() {
		return label;
	}

	/**
	 * Looks a mode up by its stored key.
	 */
	public static Optional<EmbedMode> byKey(String key) {
		if (key == null) {
			return Optional.empty();
		}
		for (EmbedMode mode : values()) {
			if (mode.key.equals(key)) {
				return Optional.of(mode);
			}
		}
		return Optional.empty();
	}

	/**
	 * The mode used when the owner has not chosen one.
	 */
	public static EmbedMode defaultMode() {
		return LIGHT;
	}
}
