package com.yamin.testimonial.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.yamin.testimonial.auth.token.RefreshTokenRepository;
import com.yamin.testimonial.auth.token.RevokedRefreshTokenRepository;
import com.yamin.testimonial.auth.token.TokenPair;
import com.yamin.testimonial.common.exception.AuthException;
import com.yamin.testimonial.common.exception.ConflictException;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.support.RecordingMailConfiguration;
import com.yamin.testimonial.support.RecordingMailSender;

import jakarta.persistence.EntityManager;

/**
 * The authentication flows of PRD 4.1.
 *
 * <p>These run against a real PostgreSQL, because most of what they check
 * lives in the database: the case-insensitive uniqueness of an email address,
 * the single use of an emailed link, and the revocation of a refresh token.
 *
 * <p>The emails are captured rather than sent, so the tests can follow the
 * links the user would have received.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(RecordingMailConfiguration.class)
class AuthServiceTest extends PostgresTestContainer {

	/** The raw token in an emailed link. */
	private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([0-9a-fA-F-]{36})");

	@Autowired
	private AuthService authService;

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

	@Autowired
	private RecordingMailSender mailSender;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void reset() {
		// The container is shared between test classes, so each test starts
		// from a known state.
		mailSender.clear();
		emailVerificationTokenRepository.deleteAll();
		passwordResetTokenRepository.deleteAll();
		revokedRefreshTokenRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		appUserRepository.deleteAll();
		entityManager.clear();
	}

	@Test
	@DisplayName("registering creates an unverified account and emails a verification link")
	void registerCreatesUnverifiedAccountAndSendsLink() {
		AppUser user = authService.register("Ada Lovelace", "ada@example.com", "password123");

		assertThat(user.getId()).isNotNull();
		assertThat(user.getEmail()).isEqualTo("ada@example.com");
		assertThat(user.getName()).isEqualTo("Ada Lovelace");
		assertThat(user.isEmailVerified()).isFalse();
		assertThat(user.getPasswordHash()).isNotBlank();

		assertThat(mailSender.verificationLinks()).hasSize(1);
		assertThat(mailSender.verificationLinks().get(0)).contains("/api/auth/verify-email?token=");
	}

	@Test
	@DisplayName("registering an email that already exists fails and creates nothing")
	void duplicateEmailIsRejectedAndCreatesNothing() {
		authService.register("Ada Lovelace", "ada@example.com", "password123");
		long before = appUserRepository.count();

		assertThatThrownBy(() -> authService.register("Someone Else", "ada@example.com", "password123"))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("already exists");

		assertThat(appUserRepository.count()).isEqualTo(before);
	}

	@Test
	@DisplayName("email uniqueness is case-insensitive")
	void duplicateEmailIsRejectedRegardlessOfCase() {
		authService.register("Ada Lovelace", "ada@example.com", "password123");

		assertThatThrownBy(() -> authService.register("Ada Again", "ADA@Example.com", "password123"))
				.isInstanceOf(ConflictException.class);

		assertThat(appUserRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("a verification link confirms the address and cannot be used twice")
	void verificationLinkIsSingleUse() {
		AppUser user = authService.register("Ada Lovelace", "ada@example.com", "password123");
		String token = tokenFrom(mailSender.verificationLinks().get(0));

		authService.verifyEmail(token);

		assertThat(appUserRepository.findById(user.getId())).get()
				.extracting(AppUser::isEmailVerified)
				.isEqualTo(true);

		assertThatThrownBy(() -> authService.verifyEmail(token))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("invalid or has expired");
	}

	@Test
	@DisplayName("an unknown verification link is rejected")
	void unknownVerificationLinkIsRejected() {
		assertThatThrownBy(() -> authService.verifyEmail(UUID.randomUUID().toString()))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("invalid or has expired");
	}

	@Test
	@DisplayName("an expired verification link is rejected")
	void expiredVerificationLinkIsRejected() {
		AppUser user = authService.register("Ada Lovelace", "ada@example.com", "password123");
		String token = tokenFrom(mailSender.verificationLinks().get(0));

		expireVerificationToken(user.getId());

		assertThatThrownBy(() -> authService.verifyEmail(token))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("invalid or has expired");

		assertThat(appUserRepository.findById(user.getId())).get()
				.extracting(AppUser::isEmailVerified)
				.isEqualTo(false);
	}

	@Test
	@DisplayName("the correct credentials sign in")
	void correctCredentialsAuthenticate() {
		authService.register("Ada Lovelace", "ada@example.com", "password123");

		AppUser user = authService.authenticate("ada@example.com", "password123");

		assertThat(user.getEmail()).isEqualTo("ada@example.com");
	}

	@Test
	@DisplayName("a wrong password and an unknown email give the same generic message")
	void wrongCredentialsGiveOneGenericMessage() {
		authService.register("Ada Lovelace", "ada@example.com", "password123");

		assertThatThrownBy(() -> authService.authenticate("ada@example.com", "wrong-password"))
				.isInstanceOf(AuthException.class)
				.hasMessage("The email or password you entered is incorrect");

		// The message must not reveal whether the address exists.
		assertThatThrownBy(() -> authService.authenticate("nobody@example.com", "wrong-password"))
				.isInstanceOf(AuthException.class)
				.hasMessage("The email or password you entered is incorrect");
	}

	@Test
	@DisplayName("an unverified account can still sign in")
	void unverifiedAccountCanSignIn() {
		AppUser user = authService.register("Ada Lovelace", "ada@example.com", "password123");

		AppUser signedIn = authService.authenticate("ada@example.com", "password123");

		assertThat(signedIn.isEmailVerified()).isFalse();
		assertThat(signedIn.getId()).isEqualTo(user.getId());
	}

	@Test
	@DisplayName("a password reset link changes the password and cannot be used twice")
	void passwordResetLinkIsSingleUse() {
		authService.register("Ada Lovelace", "ada@example.com", "password123");
		authService.requestPasswordReset("ada@example.com");

		String link = mailSender.resetLinks().get(0);
		String token = tokenFrom(link);

		authService.resetPassword(token, "newpassword123");

		// The new password works and the old one does not.
		assertThat(authService.authenticate("ada@example.com", "newpassword123")).isNotNull();
		assertThatThrownBy(() -> authService.authenticate("ada@example.com", "password123"))
				.isInstanceOf(AuthException.class);

		assertThatThrownBy(() -> authService.resetPassword(token, "anotherpassword123"))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("invalid or has expired");
	}

	@Test
	@DisplayName("a password reset request for an unknown address sends nothing")
	void passwordResetForUnknownAddressSendsNothing() {
		authService.requestPasswordReset("nobody@example.com");

		assertThat(mailSender.resetLinks()).isEmpty();
	}

	@Test
	@DisplayName("a refresh token can only be used once")
	void refreshTokenIsSingleUse() {
		AppUser user = authService.register("Ada Lovelace", "ada@example.com", "password123");
		TokenPair pair = authService.issueSession(user.getId());

		TokenPair renewed = authService.refresh(pair.refreshToken());

		assertThat(renewed.refreshToken()).isNotEqualTo(pair.refreshToken());
		assertThat(renewed.userId()).isEqualTo(user.getId());

		// The old refresh token is revoked as the new one is issued.
		assertThatThrownBy(() -> authService.refresh(pair.refreshToken()))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("session has expired");
	}

	@Test
	@DisplayName("signing out revokes the refresh token")
	void logoutRevokesRefreshToken() {
		AppUser user = authService.register("Ada Lovelace", "ada@example.com", "password123");
		TokenPair pair = authService.issueSession(user.getId());

		authService.logout(pair.refreshToken());

		assertThatThrownBy(() -> authService.refresh(pair.refreshToken()))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("session has expired");
	}

	@Test
	@DisplayName("a refresh token cannot be used as an access token")
	void refreshTokenIsNotAnAccessToken() {
		AppUser user = authService.register("Ada Lovelace", "ada@example.com", "password123");
		TokenPair pair = authService.issueSession(user.getId());

		// The controller authenticates with the access cookie only; a refresh
		// token presented there must not be accepted.
		assertThatThrownBy(() -> authService.refresh(pair.accessToken()))
				.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("changing a password ends the sessions opened with the old one")
	void passwordResetRevokesExistingSessions() {
		AppUser user = authService.register("Ada Lovelace", "ada@example.com", "password123");
		TokenPair pair = authService.issueSession(user.getId());

		authService.requestPasswordReset("ada@example.com");
		authService.resetPassword(tokenFrom(mailSender.resetLinks().get(0)), "newpassword123");

		assertThatThrownBy(() -> authService.refresh(pair.refreshToken()))
				.isInstanceOf(AuthException.class);
	}

	/**
	 * Pulls the raw token out of an emailed link.
	 *
	 * <p>The token is a UUID, and it is followed by the rest of the email, so
	 * it is matched rather than read to the end of the line.
	 */
	private String tokenFrom(String link) {
		Matcher matcher = TOKEN_PATTERN.matcher(link);
		assertThat(matcher.find()).as("the link carries a token: %s", link).isTrue();
		return matcher.group(1);
	}

	/**
	 * Backdates a verification token so it is no longer usable.
	 *
	 * <p>The update runs in its own transaction: a test method is not
	 * transactional, and Spring's {@code @Transactional} cannot be applied to a
	 * private method.
	 */
	private void expireVerificationToken(UUID userId) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.executeWithoutResult(status -> entityManager.createNativeQuery("""
				update email_verification_tokens
				set expires_at = now() - interval '1 hour'
				where user_id = :userId
				""").setParameter("userId", userId).executeUpdate());
		entityManager.clear();
	}

	@Test
	@DisplayName("emailed links last for 24 hours")
	void linkTtlIsTwentyFourHours() {
		authService.register("Ada Lovelace", "ada@example.com", "password123");

		Instant expiresAt = emailVerificationTokenRepository.findAll().get(0).getExpiresAt();
		Duration age = Duration.between(Instant.now(), expiresAt);

		assertThat(age).isBetween(Duration.ofHours(23), Duration.ofHours(25));
	}
}
