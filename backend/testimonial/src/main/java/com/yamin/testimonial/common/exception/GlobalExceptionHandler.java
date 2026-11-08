package com.yamin.testimonial.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into the error shape the frontend expects.
 *
 * <p>The frontend reads a {@code message} field off the response body to
 * surface a human-readable error (see {@code lib/api.ts}), and reads
 * {@code errors} for field-level validation messages (PRD 4.5).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * A failed authentication attempt.
	 *
	 * <p>401, with a message that never reveals which credential was wrong.
	 */
	@ExceptionHandler(AuthException.class)
	public ResponseEntity<Map<String, Object>> handleAuthException(AuthException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(body(e.getMessage()));
	}

	/**
	 * A request that conflicts with existing data.
	 *
	 * <p>409, used for an email that is already registered.
	 */
	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, Object>> handleConflictException(ConflictException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(body(e.getMessage()));
	}

	/**
	 * Field-level validation failures.
	 *
	 * <p>400, with one message per offending field so the form can show them
	 * next to the inputs.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
		Map<String, String> errors = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		Map<String, Object> body = body("Please correct the highlighted fields");
		body.put("errors", errors);
		return ResponseEntity.badRequest().body(body);
	}

	private Map<String, Object> body(String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", message);
		return body;
	}
}
