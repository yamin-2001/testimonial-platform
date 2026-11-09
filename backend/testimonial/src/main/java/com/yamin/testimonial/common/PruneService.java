package com.yamin.testimonial.common;

import java.time.Instant;

/**
 * The individual prunes, each in its own transaction.
 *
 * <p>Each prune commits on its own, so one that succeeds keeps its rows
 * removed even if the next one fails.
 */
public interface PruneService {

	/** Removes the refresh tokens that have expired. */
	int pruneLiveRefreshTokens(Instant now);

	/** Removes the revocations of refresh tokens that have expired. */
	int pruneRevokedRefreshTokens(Instant now);

	/** Removes the email verification links that have expired. */
	int pruneEmailVerificationTokens(Instant now);

	/** Removes the password reset links that have expired. */
	int prunePasswordResetTokens(Instant now);

	/** Removes the handled Stripe events that Stripe can no longer retry. */
	int pruneProcessedStripeEvents(Instant cutoff);
}
