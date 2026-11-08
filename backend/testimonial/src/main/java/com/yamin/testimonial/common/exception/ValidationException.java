package com.yamin.testimonial.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when a request is well-formed but its values are not acceptable.
 *
 * <p>Carries one message per field, so the form can show them next to the
 * inputs (PRD 4.3, 4.5). Distinct from a validation failure on the request
 * body itself, which Spring raises before the service is reached.
 */
public class ValidationException extends RuntimeException {

	private final Map<String, String> fieldErrors;

	public ValidationException(String message, Map<String, String> fieldErrors) {
		super(message);
		this.fieldErrors = new LinkedHashMap<>(fieldErrors);
	}

	public ValidationException(String field, String message) {
		super("Please correct the highlighted fields");
		this.fieldErrors = new LinkedHashMap<>();
		this.fieldErrors.put(field, message);
	}

	/** The per-field messages, keyed by field name. */
	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
}
