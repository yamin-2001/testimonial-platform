package com.yamin.testimonial.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.yamin.testimonial.billing.StripeGateway;

/**
 * Replaces the real Stripe gateway with one that records what it was asked.
 *
 * <p>Marked {@link Primary} so it wins over the auto-configured gateway. A
 * test that needs the real one does not import this configuration.
 */
@TestConfiguration
public class StubStripeGatewayConfiguration {

	@Bean
	@Primary
	public StubStripeGateway stubStripeGateway() {
		return new StubStripeGateway();
	}
}
