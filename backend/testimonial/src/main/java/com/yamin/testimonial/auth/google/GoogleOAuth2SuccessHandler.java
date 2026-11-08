package com.yamin.testimonial.auth.google;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.yamin.testimonial.auth.AuthService;
import com.yamin.testimonial.auth.token.TokenPair;
import com.yamin.testimonial.auth.web.AuthCookieService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Completes a Google sign-in by issuing the session cookies.
 *
 * <p>Google redirects back to the application after the user consents; this
 * handler turns that into a normal session, so the rest of the application
 * does not need to know which sign-in method was used.
 */
@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

	private final GoogleOAuth2Service googleOAuth2Service;

	private final AuthService authService;

	private final AuthCookieService cookieService;

	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	public GoogleOAuth2SuccessHandler(GoogleOAuth2Service googleOAuth2Service, AuthService authService,
			AuthCookieService cookieService) {
		this.googleOAuth2Service = googleOAuth2Service;
		this.authService = authService;
		this.cookieService = cookieService;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OAuth2AuthenticationToken oauth2 = (OAuth2AuthenticationToken) authentication;
		var user = googleOAuth2Service.resolveUser(oauth2);

		TokenPair pair = authService.issueSession(user.getId());
		response.addHeader("Set-Cookie",
				cookieService.accessCookie(pair.accessToken(), pair.accessTokenTtl()).toString());
		response.addHeader("Set-Cookie",
				cookieService.refreshCookie(pair.refreshToken(), pair.refreshTokenTtl()).toString());

		// The frontend reads the target from the query string it put on the
		// authorization request, so the user lands where they intended.
		String target = request.getParameter("target");
		if (target == null || !target.startsWith("/")) {
			target = "/dashboard";
		}
		redirectStrategy.sendRedirect(request, response, target);
	}
}
