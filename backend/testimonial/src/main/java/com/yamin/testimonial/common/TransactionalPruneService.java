package com.yamin.testimonial.common;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.auth.EmailVerificationTokenRepository;
import com.yamin.testimonial.auth.PasswordResetTokenRepository;
import com.yamin.testimonial.auth.token.RefreshTokenRepository;
import com.yamin.testimonial.auth.token.RevokedRefreshTokenRepository;
import com.yamin.testimonial.billing.ProcessedStripeEventRepository;

/**
 * The prunes, each running in its own transaction.
 *
 * <p>The deletes are bulk {@code @Modifying} queries, which need a
 * transaction to run in. Each method is transactional, and each is called
 * through the bean -- from {@link ScheduledCleanup}, which is a different
 * bean, so the proxy applies.
 *
 * <p>{@code REQUIRES_NEW} gives each prune its own transaction rather than
 * joining one that is already open, so that a prune which has succeeded keeps
 * its rows removed even if the next one fails.
 */
@Service
public class TransactionalPruneService implements PruneService {

	private final RefreshTokenRepository refreshTokenRepository;

	private final RevokedRefreshTokenRepository revokedRefreshTokenRepository;

	private final EmailVerificationTokenRepository emailVerificationTokenRepository;

	private final PasswordResetTokenRepository passwordResetTokenRepository;

	private final ProcessedStripeEventRepository processedStripeEventRepository;

	public TransactionalPruneService(RefreshTokenRepository refreshTokenRepository,
			RevokedRefreshTokenRepository revokedRefreshTokenRepository,
			EmailVerificationTokenRepository emailVerificationTokenRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			ProcessedStripeEventRepository processedStripeEventRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.revokedRefreshTokenRepository = revokedRefreshTokenRepository;
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.processedStripeEventRepository = processedStripeEventRepository;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int pruneLiveRefreshTokens(Instant now) {
		return refreshTokenRepository.deleteExpired(now);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int pruneRevokedRefreshTokens(Instant now) {
		return revokedRefreshTokenRepository.deleteExpired(now);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int pruneEmailVerificationTokens(Instant now) {
		return emailVerificationTokenRepository.deleteExpired(now);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int prunePasswordResetTokens(Instant now) {
		return passwordResetTokenRepository.deleteExpired(now);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int pruneProcessedStripeEvents(Instant cutoff) {
		return processedStripeEventRepository.deleteProcessedBefore(cutoff);
	}
}
