package com.yamin.testimonial.spaces;

import java.util.List;
import java.util.Optional;

/**
 * The predefined themes a Space can use (PRD 4.4).
 *
 * <p>Basic themes are available on every plan. Premium themes require
 * Premium, and the backend rejects one for a Free user rather than trusting
 * the UI to hide it.
 *
 * <p>A Space that already uses a premium theme keeps rendering it after a
 * downgrade (PRD 4.4): the check applies when a theme is <em>chosen</em>, not
 * when a Space is read.
 */
public enum Theme {

	/** A plain, light theme. */
	LIGHT("light", "Light", Tier.BASIC),

	/** A plain, dark theme. */
	DARK("dark", "Dark", Tier.BASIC),

	/** A light theme with a serif heading face. */
	EDITORIAL("editorial", "Editorial", Tier.BASIC),

	/** A dark theme with a strong accent colour. */
	MIDNIGHT("midnight", "Midnight", Tier.PREMIUM),

	/** A warm, high-contrast theme. */
	SUNSET("sunset", "Sunset", Tier.PREMIUM);

	/** Whether a theme is available on every plan or requires Premium. */
	public enum Tier {
		BASIC,
		PREMIUM
	}

	private final String key;

	private final String label;

	private final Tier tier;

	Theme(String key, String label, Tier tier) {
		this.key = key;
		this.label = label;
		this.tier = tier;
	}

	/** The value stored in {@code spaces.theme_key}. */
	public String getKey() {
		return key;
	}

	/** The name shown to the user. */
	public String getLabel() {
		return label;
	}

	/** Whether the theme requires Premium. */
	public Tier getTier() {
		return tier;
	}

	/** Whether the theme requires Premium. */
	public boolean isPremium() {
		return tier == Tier.PREMIUM;
	}

	/**
	 * Looks a theme up by its stored key.
	 */
	public static Optional<Theme> byKey(String key) {
		if (key == null) {
			return Optional.empty();
		}
		for (Theme theme : values()) {
			if (theme.key.equals(key)) {
				return Optional.of(theme);
			}
		}
		return Optional.empty();
	}

	/** The themes available on every plan. */
	public static List<Theme> basicThemes() {
		return List.of(LIGHT, DARK, EDITORIAL);
	}

	/** The themes that require Premium. */
	public static List<Theme> premiumThemes() {
		return List.of(MIDNIGHT, SUNSET);
	}

	/** Every theme, basic ones first. */
	public static List<Theme> all() {
		return List.of(LIGHT, DARK, EDITORIAL, MIDNIGHT, SUNSET);
	}
}
