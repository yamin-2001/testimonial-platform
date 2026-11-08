package com.yamin.testimonial.embed;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Space's embed configuration (PRD 4.8).
 *
 * <p>The configuration is stored in {@code spaces.embed_config} as a JSONB blob
 * and owned by this class, so its shape can change without a schema migration.
 *
 * <p>Four settings, and only one of them is free:
 * <ul>
 *   <li>the layout -- Masonry on every plan, Carousel on Premium;</li>
 *   <li>the mode -- light or dark, on every plan;</li>
 *   <li>the background colour -- Premium;</li>
 *   <li>the animation -- Premium.</li>
 * </ul>
 *
 * <p>A Space that already has a premium setting keeps it after a downgrade
 * (PRD 4.8): the check applies when a setting is <em>chosen</em>, not when a
 * Space is read. That is the same rule the premium themes follow (PRD 4.4).
 */
public final class EmbedSettings {

	/** The key the layout is stored under. */
	public static final String LAYOUT = "layout";

	/** The key the mode is stored under. */
	public static final String MODE = "mode";

	/** The key the background colour is stored under. */
	public static final String BACKGROUND_COLOR = "backgroundColor";

	/** The key the animation flag is stored under. */
	public static final String ANIMATION = "animation";

	private EmbedSettings() {
	}

	/**
	 * The configuration a new Space starts with.
	 *
	 * <p>Masonry, light, no background colour and no animation: the simplest
	 * settings, all of them free.
	 */
	public static Map<String, Object> defaults() {
		Map<String, Object> settings = new LinkedHashMap<>();
		settings.put(LAYOUT, EmbedLayout.defaultLayout().getKey());
		settings.put(MODE, EmbedMode.defaultMode().getKey());
		settings.put(BACKGROUND_COLOR, "");
		settings.put(ANIMATION, false);
		return settings;
	}

	/**
	 * The configuration as the API presents it.
	 *
	 * <p>Every key is present, so the frontend never has to cope with a missing
	 * one, and an unknown stored value falls back to the default rather than
	 * breaking the page.
	 */
	public static Map<String, Object> describe(Map<String, Object> stored) {
		Map<String, Object> described = new LinkedHashMap<>();

		described.put(LAYOUT, layoutOf(stored).getKey());
		described.put(MODE, modeOf(stored).getKey());
		described.put(BACKGROUND_COLOR, backgroundColorOf(stored));
		described.put(ANIMATION, animationOf(stored));

		return described;
	}

	/**
	 * The layout the configuration asks for.
	 */
	public static EmbedLayout layoutOf(Map<String, Object> stored) {
		Object raw = stored == null ? null : stored.get(LAYOUT);
		if (raw instanceof String key) {
			return EmbedLayout.byKey(key).orElse(EmbedLayout.defaultLayout());
		}
		return EmbedLayout.defaultLayout();
	}

	/**
	 * The mode the configuration asks for.
	 */
	public static EmbedMode modeOf(Map<String, Object> stored) {
		Object raw = stored == null ? null : stored.get(MODE);
		if (raw instanceof String key) {
			return EmbedMode.byKey(key).orElse(EmbedMode.defaultMode());
		}
		return EmbedMode.defaultMode();
	}

	/**
	 * The background colour the configuration asks for.
	 *
	 * <p>Empty means "use the mode's own background". A value that is not a
	 * plain hex colour is ignored, so a hand-edited configuration cannot put
	 * arbitrary CSS on somebody else's page.
	 */
	public static String backgroundColorOf(Map<String, Object> stored) {
		Object raw = stored == null ? null : stored.get(BACKGROUND_COLOR);
		if (raw instanceof String value && isHexColour(value)) {
			return value.trim();
		}
		return "";
	}

	/**
	 * Whether the configuration asks for animation.
	 */
	public static boolean animationOf(Map<String, Object> stored) {
		Object raw = stored == null ? null : stored.get(ANIMATION);
		if (raw instanceof Boolean value) {
			return value;
		}
		if (raw instanceof String value) {
			return Boolean.parseBoolean(value);
		}
		return false;
	}

	/**
	 * Whether a value is a hex colour such as {@code #fff} or {@code #ffffff}.
	 *
	 * <p>Only the hex form is accepted. A background colour ends up in the
	 * embed's inline styles on a third party's page, so anything that is not
	 * plainly a colour is refused rather than passed through.
	 */
	public static boolean isHexColour(String value) {
		if (value == null) {
			return false;
		}

		String trimmed = value.trim();
		if (!trimmed.startsWith("#")) {
			return false;
		}

		String digits = trimmed.substring(1);
		if (digits.length() != 3 && digits.length() != 6) {
			return false;
		}

		for (int i = 0; i < digits.length(); i++) {
			char character = digits.charAt(i);
			boolean hex = (character >= '0' && character <= '9')
					|| (character >= 'a' && character <= 'f')
					|| (character >= 'A' && character <= 'F');
			if (!hex) {
				return false;
			}
		}

		return true;
	}
}
