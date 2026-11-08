package com.yamin.testimonial.auth.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.auth.EmailVerificationTokenRepository;
import com.yamin.testimonial.auth.PasswordResetTokenRepository;
import com.yamin.testimonial.auth.token.RefreshTokenRepository;
import com.yamin.testimonial.auth.token.RevokedRefreshTokenRepository;
import com.yamin.testimonial.support.PostgresTestContainer;

/**
 * Google sign-in (PRD 4.1).
 *
 * <p>Three cases matter:
 * <ul>
 *   <li>a first-time Google sign-in creates a verified account;</li>
 *   <li>a returning Google sign-in finds the same account;</li>
 *   <li><strong>OPEN-A</strong>: signing in with Google using an email that
 *       already has a password account signs into that same account.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class GoogleOAuth2ServiceTest extends PostgresTestContainer {

	@Autowired
	private GoogleOAuth2Service googleOAuth2Service;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private RevokedRefreshTokenRepository revokedRefreshTokenRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@BeforeEach
	void reset() {
		emailVerificationTokenRepository.deleteAll();
		passwordResetTokenRepository.deleteAll();
		revokedRefreshTokenRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		appUserRepository.deleteAll();
	}

	@Test
	@DisplayName("a first-time Google sign-in creates a verified account")
	void firstTimeSignInCreatesVerifiedAccount() {
		AppUser user = googleOAuth2Service.resolveUser(
				googleToken("google-subject-1", "ada@example.com", "Ada Lovelace"));

		assertThat(user.getId()).isNotNull();
		assertThat(user.getEmail()).isEqualTo("ada@example.com");
		assertThat(user.getName()).isEqualTo("Ada Lovelace");
		assertThat(user.isEmailVerified()).isTrue();
		assertThat(user.getGoogleSubject()).isEqualTo("google-subject-1");
		// A Google account has no password to sign in with.
		assertThat(user.getPasswordHash()).isNull();
	}

	@Test
	@DisplayName("a returning Google sign-in finds the same account")
	void returningSignInFindsTheSameAccount() {
		AppUser first = googleOAuth2Service.resolveUser(
				googleToken("google-subject-1", "ada@example.com", "Ada Lovelace"));
		AppUser second = googleOAuth2Service.resolveUser(
				googleToken("google-subject-1", "ada@example.com", "Ada Lovelace"));

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(appUserRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("OPEN-A: a Google sign-in with a registered email links to that account")
	void googleSignInLinksToExistingPasswordAccount() {
		AppUser existing = new AppUser();
		existing.setEmail("ada@example.com");
		existing.setName("Ada Lovelace");
		existing.setPasswordHash("a-hash");
		existing.setEmailVerified(false);
		appUserRepository.save(existing);

		AppUser resolved = googleOAuth2Service.resolveUser(
				googleToken("google-subject-1", "ada@example.com", "Ada Lovelace"));

		// The same account, not a new one.
		assertThat(resolved.getId()).isEqualTo(existing.getId());
		assertThat(appUserRepository.count()).isEqualTo(1);

		// The Google id is recorded, so the next sign-in goes straight to it.
		assertThat(resolved.getGoogleSubject()).isEqualTo("google-subject-1");
		// The password is untouched, so the account can still sign in that way.
		assertThat(resolved.getPasswordHash()).isEqualTo("a-hash");
	}

	@Test
	@DisplayName("OPEN-A: linking works regardless of the case of the email")
	void googleSignInLinksRegardlessOfEmailCase() {
		AppUser existing = new AppUser();
		existing.setEmail("Ada@example.com");
		existing.setName("Ada Lovelace");
		existing.setPasswordHash("a-hash");
		appUserRepository.save(existing);

		AppUser resolved = googleOAuth2Service.resolveUser(
				googleToken("google-subject-1", "ada@example.com", "Ada Lovelace"));

		assertThat(resolved.getId()).isEqualTo(existing.getId());
		assertThat(appUserRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("OPEN-A: a linked account is found by its Google id on the next sign-in")
	void linkedAccountIsFoundByGoogleSubject() {
		AppUser existing = new AppUser();
		existing.setEmail("ada@example.com");
		existing.setName("Ada Lovelace");
		existing.setPasswordHash("a-hash");
		appUserRepository.save(existing);

		googleOAuth2Service.resolveUser(
				googleToken("google-subject-1", "ada@example.com", "Ada Lovelace"));
		AppUser second = googleOAuth2Service.resolveUser(
				googleToken("google-subject-1", "ada@example.com", "Ada Lovelace"));

		assertThat(second.getId()).isEqualTo(existing.getId());
		assertThat(appUserRepository.count()).isEqualTo(1);
	}

	/**
	 * Builds the token Spring Security hands to the success handler.
	 */
	private OAuth2AuthenticationToken googleToken(String subject, String email, String name) {
		OAuth2User principal = new DefaultOAuth2User(
				AuthorityUtils.createAuthorityList("ROLE_USER"),
				Map.of("sub", subject, "email", email, "name", name),
				"sub");

		return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
	}
}
