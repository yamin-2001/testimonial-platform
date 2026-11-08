package com.yamin.testimonial.auth.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.yamin.testimonial.auth.google.GoogleOAuth2SuccessHandler;
import com.yamin.testimonial.auth.web.JwtAuthenticationFilter;

/**
 * Security configuration for the API.
 *
 * <p>The application is stateless: the session lives in a signed JWT carried
 * by an HttpOnly cookie, so there is no server-side HTTP session and no
 * CSRF token to manage. CSRF protection is therefore disabled -- the cookie
 * is SameSite=Lax and the API is reached same-origin through the frontend's
 * rewrite, so a cross-site request cannot carry it.
 *
 * <p>Unauthenticated requests to protected endpoints return 401 rather than
 * redirecting to a login page, because this is a JSON API consumed by the
 * frontend (PRD 4.1).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	private final GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.googleOAuth2SuccessHandler = googleOAuth2SuccessHandler;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				// Stateless API: no session is created, so there is no CSRF token.
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Public: the sign-in and account-recovery endpoints.
						.requestMatchers("/api/auth/login", "/api/auth/register",
								"/api/auth/refresh", "/api/auth/logout",
								"/api/auth/forgot-password", "/api/auth/reset-password",
								"/api/auth/verify-email", "/api/auth/me")
						.permitAll()
						// The Google OAuth2 flow: the redirect to Google's consent
						// screen and the callback that returns with the code.
						.requestMatchers("/oauth2/**", "/login/**").permitAll()
						// Everything else requires an authenticated user.
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions
						// A missing or invalid token is a 401, not a redirect.
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.oauth2Login(oauth2 -> oauth2
						// After Google redirects back, issue the same session
						// cookies an email/password sign-in would.
						.successHandler(googleOAuth2SuccessHandler))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	/**
	 * CORS configuration.
	 *
	 * <p>The frontend calls the API through a Next.js rewrite, so requests
	 * arrive same-origin and CORS is not strictly needed. It is configured
	 * anyway to allow direct calls during development, with credentials
	 * enabled so the auth cookies are still sent.
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(List.of("http://localhost:3000"));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
