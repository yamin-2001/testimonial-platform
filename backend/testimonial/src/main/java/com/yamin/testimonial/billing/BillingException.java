package com.yamin.testimonial.billing;

/**
 * A billing operation that Stripe refused or could not complete (PRD 4.9).
 *
 * <p>The Stripe detail is kept as the cause but never shown to the user: the
 * message is the application's own, so an internal Stripe failure does not
 * leak an API key, a price id or a Stripe error code into the browser.
 */
public class BillingException extends RuntimeException {

	public BillingException(String message) {
		super(message);
	}

	public BillingException(String message, Throwable cause) {
		super(message, cause);
	}
}
