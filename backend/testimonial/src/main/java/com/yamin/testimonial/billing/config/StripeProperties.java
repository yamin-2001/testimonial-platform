package com.yamin.testimonial.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe settings, bound from the {@code app.stripe} configuration prefix
 * (PRD 4.9).
 *
 * <p>Blank keys mean Stripe is not configured. The application still boots,
 * the Billing page still shows the plan, and the Upgrade button explains
 * that billing is not set up rather than failing with a Stripe error. That
 * keeps the development and test flows working without a Stripe account.
 */
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

	/**
	 * The secret key used to call Stripe's API.
	 *
	 * <p>Blank means Stripe is not configured.
	 */
	private String secretKey = "";

	/**
	 * The publishable key, shown to the browser.
	 */
	private String publishableKey = "";

	/**
	 * The signing secret used to verify webhook signatures.
	 *
	 * <p>Blank means webhooks cannot be verified, and the webhook endpoint
	 * refuses every request. An unverified webhook must never change a
	 * subscription (PRD 6.15).
	 */
	private String webhookSecret = "";

	/**
	 * The Stripe price id for the Premium plan.
	 *
	 * <p>Blank means there is nothing to check out.
	 */
	private String premiumPriceId = "";

	/**
	 * Where Stripe sends the user after a successful checkout.
	 *
	 * <p>The plan is <em>not</em> changed by this redirect. Only a verified
	 * webhook changes the plan (PRD 4.9).
	 */
	private String successUrl = "";

	/**
	 * Where Stripe sends the user if they abandon the checkout.
	 */
	private String cancelUrl = "";

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getPublishableKey() {
		return publishableKey;
	}

	public void setPublishableKey(String publishableKey) {
		this.publishableKey = publishableKey;
	}

	public String getWebhookSecret() {
		return webhookSecret;
	}

	public void setWebhookSecret(String webhookSecret) {
		this.webhookSecret = webhookSecret;
	}

	public String getPremiumPriceId() {
		return premiumPriceId;
	}

	public void setPremiumPriceId(String premiumPriceId) {
		this.premiumPriceId = premiumPriceId;
	}

	public String getSuccessUrl() {
		return successUrl;
	}

	public void setSuccessUrl(String successUrl) {
		this.successUrl = successUrl;
	}

	public String getCancelUrl() {
		return cancelUrl;
	}

	public void setCancelUrl(String cancelUrl) {
		this.cancelUrl = cancelUrl;
	}

	/**
	 * Whether Stripe is configured well enough to take a payment.
	 */
	public boolean isConfigured() {
		return notBlank(secretKey) && notBlank(premiumPriceId);
	}

	/**
	 * Whether webhook signatures can be verified.
	 */
	public boolean hasWebhookSecret() {
		return notBlank(webhookSecret);
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
