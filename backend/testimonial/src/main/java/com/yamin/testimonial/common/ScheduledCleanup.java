package com.yamin.testimonial.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes the rows that have served their purpose.
 *
 * <p>Five tables in this schema only ever grow: the two refresh token tables,
 * the two email link tables and the log of handled Stripe events. Every one of
 * them holds rows that can no longer do anything -- a token past its expiry
 * can never be redeemed, a Stripe event past the retry window can never be
 * replayed -- and every one of them had a delete written for it that nothing
 * ever called.
 *
 * <p>This job is what calls them. It runs once a day, which is more often
 * than any of the rows need; the tables are small, and the point is that they
 * do not grow without bound.
 *
 * <p>Each table is pruned in its own transaction by {@link PruneService}, so
 * a failure in one of them does not roll back the ones that already
 * succeeded, and does not stop the ones that follow.
 */
@Component
public class ScheduledCleanup {

	private static final Logger LOG = LoggerFactory.getLogger(ScheduledCleanup.class);

	/**
	 * How long a handled Stripe event is kept.
	 *
	 * <p>Stripe retries an event it thinks was not acknowledged for up to
	 * three days. The record has to outlive that window, or a late retry would
	 * not be recognised as a replay and would apply its change a second time.
	 *
	 * <p>The margin on top of it is deliberate: the window is a limit Stripe
	 * may extend, not one it guarantees.
	 */
	public static final Duration STRIPE_EVENT_RETENTION = Duration.ofDays(7);

	private final PruneService pruneService;

	private final Clock clock;

	/**
	 * The constructor Spring uses.
	 *
	 * <p>There are two constructors, so this one is marked: without it Spring
	 * cannot tell which to use.
	 */
	@Autowired
	public ScheduledCleanup(PruneService pruneService) {
		this(pruneService, Clock.systemUTC());
	}

	/**
	 * The constructor the tests use, with a clock they control.
	 */
	public ScheduledCleanup(PruneService pruneService, Clock clock) {
		this.pruneService = pruneService;
		this.clock = clock;
	}

	/**
	 * Prunes the rows that can no longer do anything.
	 *
	 * <p>Runs in the early morning, when there is little traffic, and once a
	 * day is often enough: the tables it touches grow slowly.
	 */
	@Scheduled(cron = "0 17 3 * * *")
	public void prune() {
		Instant now = clock.instant();

		prune("live refresh tokens", () -> pruneService.pruneLiveRefreshTokens(now));
		prune("revoked refresh tokens", () -> pruneService.pruneRevokedRefreshTokens(now));
		prune("email verification tokens", () -> pruneService.pruneEmailVerificationTokens(now));
		prune("password reset tokens", () -> pruneService.prunePasswordResetTokens(now));

		Instant stripeCutoff = now.minus(STRIPE_EVENT_RETENTION);
		prune("handled Stripe events", () -> pruneService.pruneProcessedStripeEvents(stripeCutoff));
	}

	/**
	 * Prunes one table, and does not let a failure in it stop the others.
	 */
	private void prune(String what, PruneOperation operation) {
		try {
			int removed = operation.run();
			if (removed > 0) {
				LOG.info("Pruned {} expired {}", removed, what);
			}
		}
		catch (RuntimeException e) {
			// One table failing must not stop the others, and must not fail the
			// job: the next run will try again.
			LOG.error("Could not prune {}; the others were still pruned", what, e);
		}
	}

	/**
	 * A prune that returns how many rows it removed.
	 */
	@FunctionalInterface
	private interface PruneOperation {
		int run();
	}
}
