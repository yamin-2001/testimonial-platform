package com.yamin.testimonial.common.exception;

/**
 * Raised when something the caller asked for does not exist.
 *
 * <p>Used for another user's Space: the request is answered with 404 rather
 * than 403, so the response does not reveal that the Space exists (PRD 4.2,
 * 8).
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
