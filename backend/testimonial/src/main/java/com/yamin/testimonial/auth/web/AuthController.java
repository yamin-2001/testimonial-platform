package com.yamin.testimonial.auth.web;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.yamin.testimonial.auth.AuthService;
import com.yamin.testimonial.auth.appuser.AppUserLookup;
import com.yamin.testimonial.auth.token.TokenPair;
import com.yamin.testimonial.auth.web.dto.AuthDtos.AuthResponse;
import com.yamin.testimonial.auth.web.dto.AuthDtos.CurrentUserResponse;
import com.yamin.testimonial.auth.web.dto.AuthDtos.ForgotPasswordRequest;
import com.yamin.testimonial.auth.web.dto.AuthDtos.LoginRequest;
import com.yamin.testimonial.auth.web.dto.AuthDtos.RegisterRequest;
import com.yamin.testimonial.auth.web.dto.AuthDtos.ResetPasswordRequest;
import com.yamin.testimonial.auth.web.dto.AuthDtos.VerifyEmailRequest;

import jakarta.validation.Valid;

/**
 * The authentication endpoints (PRD 4.1).
 *
 * <p>Session tokens are delivered in HttpOnly cookies, so the browser sends
 * them automatically and page scripts can never read them.
 */
@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

	private final AuthService authService;

	private final AuthCookieService cookieService;

	private final AppUserLookup appUserLookup;

	public AuthController(AuthService authService, AuthCookieService cookieService,
			AppUserLookup appUserLookup) {
		this.authService = authService;
		this.cookieService = cookieService;
		this.appUserLookup = appUserLookup;
	}

	/**
	 * Registers a new account.
	 *
	 * <p>The response is the same shape as a sign-in, but no session cookie is
	 * set: the address still needs to be verified first.
	 */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		var user = authService.register(request.name(), request.email(), request.password());
		return new AuthResponse(authService.describe(user), user.isEmailVerified());
	}

	/**
	 * Signs in and sets the session cookies.
	 */
	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		var user = authService.authenticate(request.email(), request.password());
		TokenPair pair = authService.issueSession(user.getId());

		ResponseEntity.BodyBuilder response = ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookieService.accessCookie(pair.accessToken(), pair.accessTokenTtl())
						.toString())
				.header(HttpHeaders.SET_COOKIE,
						cookieService.refreshCookie(pair.refreshToken(), pair.refreshTokenTtl()).toString());

		return response.body(new AuthResponse(authService.describe(user), user.isEmailVerified()));
	}

	/**
	 * Signs out by revoking the refresh token and clearing the cookies.
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = "${app.auth.refresh-token-cookie:refresh}", required = false) String refreshToken) {
		authService.logout(refreshToken);

		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.NO_CONTENT);
		response.header(HttpHeaders.SET_COOKIE,
				cookieService.expiredCookie(cookieService.refreshCookieName()).toString());
		response.header(HttpHeaders.SET_COOKIE,
				cookieService.expiredCookie(cookieService.accessCookieName()).toString());

		return response.build();
	}

	/**
	 * Exchanges the refresh token for a new session.
	 */
	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(
			@CookieValue(name = "${app.auth.refresh-token-cookie:refresh}", required = false) String refreshToken) {
		TokenPair pair = authService.refresh(refreshToken);
		UUID userId = appUserLookup.idOf(pair);

		ResponseEntity.BodyBuilder response = ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookieService.accessCookie(pair.accessToken(), pair.accessTokenTtl())
						.toString())
				.header(HttpHeaders.SET_COOKIE,
						cookieService.refreshCookie(pair.refreshToken(), pair.refreshTokenTtl()).toString());

		CurrentUserResponse user = appUserLookup.describe(userId);
		return response.body(new AuthResponse(user, user.emailVerified()));
	}

	/**
	 * Returns the signed-in user, or 401 if there is no valid session.
	 */
	@GetMapping("/me")
	public CurrentUserResponse me(@AuthenticationPrincipal UUID userId) {
		return appUserLookup.describe(userId);
	}

	/**
	 * Confirms an email address.
	 *
	 * <p>Accepts the token either as a query parameter -- which is what the
	 * link in the email uses, so clicking it works -- or in the body.
	 */
	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmail(@RequestParam(value = "token", required = false) String tokenParam,
			@RequestBody(required = false) VerifyEmailRequest body) {
		String token = tokenParam != null ? tokenParam : (body == null ? null : body.token());
		authService.verifyEmail(token);
	}

	/**
	 * Starts a password reset.
	 *
	 * <p>Always answers 204, whether or not the address is registered.
	 */
	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.requestPasswordReset(request.email());
	}

	/**
	 * Completes a password reset.
	 */
	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@RequestParam(value = "token", required = false) String tokenParam,
			@RequestBody(required = false) ResetPasswordRequest body) {
		String token = tokenParam != null ? tokenParam : (body == null ? null : body.token());
		authService.resetPassword(token, body == null ? null : body.password());
	}
}
