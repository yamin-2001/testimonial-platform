package com.yamin.testimonial.testimonials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.yamin.testimonial.testimonials.config.TurnstileProperties;

/**
 * Verifying a CAPTCHA token with Cloudflare (PRD 4.5).
 *
 * <p>The Cloudflare call is stubbed, so the test proves the request shape and
 * the reading of the answer without reaching the network.
 */
class CloudflareTurnstileVerifierTest {

	private static final String VERIFY_URL = "https://challenges.example.com/siteverify";

	private TurnstileProperties properties;

	private RestClient.Builder builder;

	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		properties = new TurnstileProperties();
		properties.setSecretKey("a-secret-key");
		properties.setVerifyUrl(VERIFY_URL);

		builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
	}

	@Test
	@DisplayName("a successful verification is accepted")
	void aSuccessIsAccepted() {
		server.expect(requestTo(VERIFY_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("a-secret-key")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("a-token")))
				.andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

		CloudflareTurnstileVerifier verifier = new CloudflareTurnstileVerifier(properties, builder);

		assertThat(verifier.verify("a-token", "203.0.113.9")).isTrue();
		server.verify();
	}

	@Test
	@DisplayName("a failed verification is rejected")
	void aFailureIsRejected() {
		server.expect(requestTo(VERIFY_URL))
				.andRespond(withSuccess(
						"{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
						MediaType.APPLICATION_JSON));

		CloudflareTurnstileVerifier verifier = new CloudflareTurnstileVerifier(properties, builder);

		assertThat(verifier.verify("a-token", "203.0.113.9")).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("a missing token is rejected without calling Cloudflare")
	void aMissingTokenIsRejectedWithoutCallingCloudflare() {
		CloudflareTurnstileVerifier verifier = new CloudflareTurnstileVerifier(properties, builder);

		assertThat(verifier.verify(null, "203.0.113.9")).isFalse();
		assertThat(verifier.verify("  ", "203.0.113.9")).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("a Cloudflare failure rejects the submission rather than allowing it")
	void aCloudflareFailureRejects() {
		server.expect(requestTo(VERIFY_URL))
				.andRespond(withServerError());

		CloudflareTurnstileVerifier verifier = new CloudflareTurnstileVerifier(properties, builder);

		// A CAPTCHA that cannot be checked must not let the submission
		// through.
		assertThat(verifier.verify("a-token", "203.0.113.9")).isFalse();
	}

	@Test
	@DisplayName("the verifier is not configured without a secret")
	void theVerifierIsNotConfiguredWithoutASecret() {
		TurnstileProperties blank = new TurnstileProperties();

		assertThat(blank.isConfigured()).isFalse();

		TurnstileProperties set = new TurnstileProperties();
		set.setSecretKey("a-secret-key");
		assertThat(set.isConfigured()).isTrue();
	}
}
