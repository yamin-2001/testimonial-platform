package com.yamin.testimonial.auth.google;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;

/**
 * Creates or finds the account behind a successful Google sign-in (PRD 4.1).
 *
 * <p>Google users are treated as verified, because Google has already
 * confirmed the address.
 *
 * <p><strong>OPEN-A.</strong> Signing in with Google using an email that
 * already has an email/password account signs into that same account. The
 * lookup order is deliberate:
 * <ol>
 *   <li>by Google's user id, which identifies a returning Google sign-in;</li>
 *   <li>by email, which finds a password account to link;</li>
 *   <li>otherwise a new verified account is created.</li>
 * </ol>
 */
@Service
public class GoogleOAuth2Service {

	private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2Service.class);

	private final AppUserRepository appUserRepository;

	public GoogleOAuth2Service(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	/**
	 * Resolves the account for a Google sign-in, creating or linking it as
	 * needed.
	 */
	@Transactional
	public AppUser resolveUser(OAuth2AuthenticationToken authentication) {
		String googleSubject = authentication.getName();
		String email = extractEmail(authentication);

		Optional<AppUser> bySubject = appUserRepository.findByGoogleSubject(googleSubject);
		if (bySubject.isPresent()) {
			return bySubject.get();
		}

		if (email != null) {
			Optional<AppUser> byEmail = appUserRepository.findByEmailIgnoreCase(email);
			if (byEmail.isPresent()) {
				AppUser user = byEmail.get();
				user.setGoogleSubject(googleSubject);
				log.info("Linking Google sign-in to the existing account for {}", email);
				return appUserRepository.save(user);
			}
		}

		AppUser user = new AppUser();
		user.setName(extractName(authentication, email));
		user.setEmail(email != null ? email : googleSubject + "@google.local");
		user.setPasswordHash(null);
		user.setEmailVerified(true);
		user.setGoogleSubject(googleSubject);
		log.info("Creating an account for a first-time Google sign-in");
		return appUserRepository.save(user);
	}

	private String extractEmail(OAuth2AuthenticationToken authentication) {
		OAuth2User principal = authentication.getPrincipal();
		Object email = principal.getAttribute("email");
		return email != null ? email.toString() : null;
	}

	private String extractName(OAuth2AuthenticationToken authentication, String email) {
		OAuth2User principal = authentication.getPrincipal();
		Object name = principal.getAttribute("name");
		if (name != null && !name.toString().isBlank()) {
			return name.toString();
		}
		Object givenName = principal.getAttribute("given_name");
		if (givenName != null && !givenName.toString().isBlank()) {
			return givenName.toString();
		}
		return email != null ? email : "Google User";
	}
}
