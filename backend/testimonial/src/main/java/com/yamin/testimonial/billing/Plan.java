package com.yamin.testimonial.billing;

/**
 * The subscription tier a user is on.
 *
 * <p>Limits per plan are defined in PRD 7 and enforced on the backend for
 * Space creation, public submission and embed configuration saves.
 */
public enum Plan {

	/** 3 Spaces, 100 testimonials per Space, basic themes, Masonry layout. */
	FREE,

	/** 25 Spaces, 1,000 testimonials per Space, premium themes, Carousel layout. */
	PREMIUM
}
