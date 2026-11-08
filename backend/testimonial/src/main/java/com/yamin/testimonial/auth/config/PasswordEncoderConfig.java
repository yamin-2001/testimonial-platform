package com.yamin.testimonial.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing.
 *
 * <p>Kept in its own configuration class rather than in
 * {@link SecurityConfig}: {@code AuthService} depends on the encoder, and the
 * OAuth2 success handler depends on {@code AuthService}, so declaring the
 * encoder alongside the security filter chain would create a dependency cycle.
 *
 * <p>BCrypt with the default strength (PRD 8).
 */
@Configuration
public class PasswordEncoderConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
