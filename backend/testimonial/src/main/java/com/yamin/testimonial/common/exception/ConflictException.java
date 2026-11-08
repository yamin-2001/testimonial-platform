package com.yamin.testimonial.common.exception;

/**
 * Raised when a request conflicts with existing data.
 *
 * <p>Used for an email that is already registered (PRD 4.1) and for a slug
 * that is already taken (PRD 4.2).
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
