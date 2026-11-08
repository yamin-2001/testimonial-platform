package com.yamin.testimonial.auth.web;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.yamin.testimonial.auth.config.AuthProperties;
import com.yamin.testimonial.auth.token.JwtService;
import com.yamin.testimonial.auth.token.JwtService.TokenType;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates requests from the access-token cookie.
 *
 * <p>The cookie is read on every request and, if it carries a valid access
 * token, the request is authenticated as that user. A missing or invalid
 * token is not an error here -- the request simply stays anonymous, and the
 * authorization rules decide whether that is acceptable. Unauthenticated
 * requests to protected endpoints therefore return 401 (PRD 4.1).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	private final AuthProperties properties;

	public JwtAuthenticationFilter(JwtService jwtService, AuthProperties properties) {
		this.jwtService = jwtService;
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String token = readAccessToken(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			Claims claims = jwtService.parse(token, TokenType.ACCESS);
			if (claims != null) {
				authenticate(request, claims);
			}
		}

		filterChain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request, Claims claims) {
		try {
			UUID userId = UUID.fromString(claims.getSubject());
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		catch (IllegalArgumentException e) {
			// The subject is not a UUID, so the token was not issued by us.
			SecurityContextHolder.clearContext();
		}
	}

	private String readAccessToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (properties.getAccessTokenCookie().equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
