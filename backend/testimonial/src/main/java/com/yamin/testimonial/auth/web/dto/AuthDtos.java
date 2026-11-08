package com.yamin.testimonial.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The request and response bodies of the authentication endpoints.
 */
public final class AuthDtos {

	private AuthDtos() {
	}

	/**
	 * Registration request.
	 *
	 * <p>The password must be at least 8 characters (PRD 4.1).
	 */
	public record RegisterRequest(
			@NotBlank(message = "Name is required")
			@Size(max = 120, message = "Name must be at most 120 characters")
			String name,

			@NotBlank(message = "Email is required")
			@Email(message = "Please enter a valid email address")
			@Size(max = 255, message = "Email must be at most 255 characters")
			String email,

			@NotBlank(message = "Password is required")
			@Size(min = 8, message = "Password must be at least 8 characters")
			String password) {
	}

	/** Login request. */
	public record LoginRequest(
			@NotBlank(message = "Email is required")
			@Email(message = "Please enter a valid email address")
			String email,

			@NotBlank(message = "Password is required")
			String password) {
	}

	/** Password reset request: the email whose password should be reset. */
	public record ForgotPasswordRequest(
			@NotBlank(message = "Email is required")
			@Email(message = "Please enter a valid email address")
			String email) {
	}

	/** Password reset request carrying the token from the emailed link. */
	public record ResetPasswordRequest(
			@NotBlank(message = "Token is required")
			String token,

			@NotBlank(message = "Password is required")
			@Size(min = 8, message = "Password must be at least 8 characters")
			String password) {
	}

	/** Email verification request carrying the token from the emailed link. */
	public record VerifyEmailRequest(
			@NotBlank(message = "Token is required")
			String token) {
	}

	/**
	 * The signed-in user, as returned by {@code GET /api/auth/me}.
	 *
	 * @param id the user's id
	 * @param name the user's display name
	 * @param email the user's email address
	 * @param emailVerified whether the address has been confirmed
	 */
	public record CurrentUserResponse(
			String id,
			String name,
			String email,
			boolean emailVerified) {
	}

	/**
	 * The response returned after a successful sign-in or registration.
	 *
	 * @param user the signed-in user
	 * @param emailVerified whether the address still needs confirming
	 */
	public record AuthResponse(
			CurrentUserResponse user,
			boolean emailVerified) {
	}
}
