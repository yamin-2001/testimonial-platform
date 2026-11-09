package com.yamin.testimonial.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.auth.EmailVerificationToken;
import com.yamin.testimonial.auth.EmailVerificationTokenRepository;
import com.yamin.testimonial.auth.PasswordResetToken;
import com.yamin.testimonial.auth.PasswordResetTokenRepository;
import com.yamin.testimonial.auth.token.RefreshToken;
import com.yamin.testimonial.auth.token.RefreshTokenRepository;
import com.yamin.testimonial.auth.token.RevokedRefreshToken;
import com.yamin.testimonial.auth.token.RevokedRefreshTokenRepository;
import com.yamin.testimonial.billing.ProcessedStripeEvent;
import com.yamin.testimonial.billing.ProcessedStripeEventRepository;
import com.yamin.testimonial.support.PostgresTestContainer;

/**
 * The daily cleanup of rows that can no longer do anything.
 *
 * <p>Five tables in this schema only ever grow, and each of them had a delete
 * written for it that nothing ever called. This test proves the deletes now
 * run, and that they remove the right rows: the expired ones, and only the
 * expired ones.
 *
 * <p>Runs against a real PostgreSQL, because the deletes are written in
 * JPQL and H2 would not agree with them about the date comparison.
 */
@SpringBootTest
@ActiveProfiles("test")
class ScheduledCleanupTest extends PostgresTestContainer {

	/** The moment the cleanup believes it is. */
	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private RevokedRefreshTokenRepository revokedRefreshTokenRepository;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private ProcessedStripeEventRepository processedStripeEventRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PruneService pruneService;

	private AppUser user;

	private ScheduledCleanup cleanup;

	@BeforeEach
	void setUp() {
		refreshTokenRepository.deleteAll();
		revokedRefreshTokenRepository.deleteAll();
		emailVerificationTokenRepository.deleteAll();
		passwordResetTokenRepository.deleteAll();
		processedStripeEventRepository.deleteAll();
		appUserRepository.deleteAll();

		user = new AppUser();
		user.setEmail("owner-" + UUID.randomUUID() + "@example.com");
		user.setEmailVerified(true);
		appUserRepository.save(user);

		cleanup = new ScheduledCleanup(pruneService,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("an expired live refresh token is removed and a live one is kept")
	void expiredLiveRefreshTokensArePruned() {
		refreshTokenRepository.save(refreshToken("expired", NOW.minus(Duration.ofDays(1))));
		refreshTokenRepository.save(refreshToken("live", NOW.plus(Duration.ofDays(1))));

		cleanup.prune();

		assertThat(refreshTokenRepository.findAllByUserId(user.getId()))
				.extracting(RefreshToken::getJti)
				.containsExactly("live");
	}

	@Test
	@DisplayName("an expired revocation is removed and a live one is kept")
	void expiredRevocationsArePruned() {
		revokedRefreshTokenRepository.save(
				new RevokedRefreshToken("expired", NOW.minus(Duration.ofDays(1)), NOW));
		revokedRefreshTokenRepository.save(
				new RevokedRefreshToken("live", NOW.plus(Duration.ofDays(1)), NOW));

		cleanup.prune();

		assertThat(revokedRefreshTokenRepository.findAll())
				.extracting(RevokedRefreshToken::getJti)
				.containsExactly("live");
	}

	@Test
	@DisplayName("an expired email verification link is removed and a live one is kept")
	void expiredEmailVerificationTokensArePruned() {
		emailVerificationTokenRepository.save(emailVerificationToken("expired", NOW.minus(Duration.ofDays(1))));
		emailVerificationTokenRepository.save(emailVerificationToken("live", NOW.plus(Duration.ofDays(1))));

		cleanup.prune();

		assertThat(emailVerificationTokenRepository.findAll())
				.extracting(EmailVerificationToken::getTokenHash)
				.containsExactly("live");
	}

	@Test
	@DisplayName("an expired password reset link is removed and a live one is kept")
	void expiredPasswordResetTokensArePruned() {
		passwordResetTokenRepository.save(passwordResetToken("expired", NOW.minus(Duration.ofDays(1))));
		passwordResetTokenRepository.save(passwordResetToken("live", NOW.plus(Duration.ofDays(1))));

		cleanup.prune();

		assertThat(passwordResetTokenRepository.findAll())
				.extracting(PasswordResetToken::getTokenHash)
				.containsExactly("live");
	}

	@Test
	@DisplayName("a Stripe event is kept until Stripe can no longer retry it")
	void handledStripeEventsArePrunedOnlyAfterTheRetryWindow() {
		// Older than the retention window: Stripe cannot retry it any more.
		processedStripeEventRepository.save(new ProcessedStripeEvent("old", "invoice.paid",
				NOW.minus(ScheduledCleanup.STRIPE_EVENT_RETENTION).minus(Duration.ofDays(1))));
		// Inside the window: Stripe may still retry it, so the record must stay.
		processedStripeEventRepository.save(new ProcessedStripeEvent("recent", "invoice.paid",
				NOW.minus(Duration.ofDays(1))));

		cleanup.prune();

		assertThat(processedStripeEventRepository.findAll())
				.extracting(ProcessedStripeEvent::getEventId)
				.containsExactly("recent");
	}

	@Test
	@DisplayName("one failing table does not stop the others being pruned")
	void oneFailingPruneDoesNotStopTheOthers() {
		refreshTokenRepository.save(refreshToken("expired", NOW.minus(Duration.ofDays(1))));
		emailVerificationTokenRepository.save(emailVerificationToken("expired", NOW.minus(Duration.ofDays(1))));

		// A decorator around the real bean: the first prune fails, and the
		// rest are the real ones, so they still run in their transactions.
		PruneService failing = new PruneService() {
			@Override
			public int pruneLiveRefreshTokens(Instant now) {
				throw new IllegalStateException("the database is unavailable");
			}

			@Override
			public int pruneRevokedRefreshTokens(Instant now) {
				return pruneService.pruneRevokedRefreshTokens(now);
			}

			@Override
			public int pruneEmailVerificationTokens(Instant now) {
				return pruneService.pruneEmailVerificationTokens(now);
			}

			@Override
			public int prunePasswordResetTokens(Instant now) {
				return pruneService.prunePasswordResetTokens(now);
			}

			@Override
			public int pruneProcessedStripeEvents(Instant cutoff) {
				return pruneService.pruneProcessedStripeEvents(cutoff);
			}
		};

		new ScheduledCleanup(failing, Clock.fixed(NOW, ZoneOffset.UTC)).prune();

		// The table after the failing one was still pruned.
		assertThat(emailVerificationTokenRepository.findAll()).isEmpty();
		// And the failing one was left alone rather than half-done.
		assertThat(refreshTokenRepository.findAllByUserId(user.getId())).hasSize(1);
	}

	private RefreshToken refreshToken(String jti, Instant expiresAt) {
		return new RefreshToken(jti, user.getId(), expiresAt, NOW.minus(Duration.ofDays(2)));
	}

	private EmailVerificationToken emailVerificationToken(String hash, Instant expiresAt) {
		EmailVerificationToken token = new EmailVerificationToken();
		token.setUser(user);
		token.setTokenHash(hash);
		token.setExpiresAt(expiresAt);
		return token;
	}

	private PasswordResetToken passwordResetToken(String hash, Instant expiresAt) {
		PasswordResetToken token = new PasswordResetToken();
		token.setUser(user);
		token.setTokenHash(hash);
		token.setExpiresAt(expiresAt);
		return token;
	}
}
