package com.yamin.testimonial.embed;

import java.util.Optional;

/**
 * How the embed lays its testimonials out (PRD 4.8).
 *
 * <p>Masonry is available on every plan. Carousel requires Premium, and the
 * backend rejects it for a Free user rather than trusting the UI to hide it
 * (PRD 6.12).
 */
public enum EmbedLayout {

	/** A masonry grid of cards. Available on every plan. */
	MASONRY("masonry", "Masonry", Tier.FREE),

	/** A carousel of cards that slides. Requires Premium. */
	CAROUSEL("carousel", "Carousel", Tier.PREMIUM);

	/** Whether a layout is available on every plan or requires Premium. */
	public enum Tier {
		FREE,
		PREMIUM
	}

	private final String key;

	private final String label;

	private final Tier tier;

	EmbedLayout(String key, String label, Tier tier) {
		this.key = key;
		this.label = label;
		this.tier = tier;
	}

	/** The value stored in {@code spaces.embed_config}. */
	public String getKey() {
		return key;
	}

	/** The name shown to the user. */
	public String getLabel() {
		return label;
	}

	/** Whether the layout requires Premium. */
	public boolean isPremium() {
		return tier == Tier.PREMIUM;
	}

	/**
	 * Looks a layout up by its stored key.
	 */
	public static Optional<EmbedLayout> byKey(String key) {
		if (key == null) {
			return Optional.empty();
		}
		for (EmbedLayout layout : values()) {
			if (layout.key.equals(key)) {
				return Optional.of(layout);
			}
		}
		return Optional.empty();
	}

	/**
	 * The layout used when the owner has not chosen one.
	 */
	public static EmbedLayout defaultLayout() {
		return MASONRY;
	}
}
