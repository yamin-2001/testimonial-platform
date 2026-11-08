package com.yamin.testimonial.common.exception;

/**
 * Raised when an authentication attempt fails.
 *
 * <p>Wrong credentials deliberately raise this with a generic message that
 * does not reveal whether the email or the password was wrong (PRD 4.1).
 */
public class AuthException extends RuntimeException {

	public AuthException(String message) {
		super(message);
	}
}
