package com.yamin.testimonial.testimonials;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.yamin.testimonial.testimonials.config.TurnstileProperties;

/**
 * Verifies a CAPTCHA token with Cloudflare Turnstile (PRD 4.5).
 *
 * <p>The token is posted to Cloudflare's siteverify endpoint along with the
 * secret key. Cloudflare answers with a JSON object whose {@code success} flag
 * says whether the token was valid.
 *
 * <p>A failure to reach Cloudflare is treated as a failed check. That is the
 * safe direction: a CAPTCHA that cannot be verified must not let the
 * submission through.
 */
@Component
public class CloudflareTurnstileVerifier implements TurnstileVerifier {

	private static final Logger log = LoggerFactory.getLogger(CloudflareTurnstileVerifier.class);

	private final TurnstileProperties properties;

	private final RestClient restClient;

	/**
	 * The verifier the application uses.
	 *
	 * <p>The application runs on {@code spring-boot-starter-webmvc}, which
	 * does not bring the {@code RestClient} auto-configuration with it, so
	 * the client is built here rather than injected.
	 */
	@Autowired
	public CloudflareTurnstileVerifier(TurnstileProperties properties) {
		this(properties, RestClient.builder());
	}

	/**
	 * A verifier with a client of its own.
	 *
	 * <p>The test binds a mock server to the builder, so the request can be
	 * checked without reaching the network.
	 */
	public CloudflareTurnstileVerifier(TurnstileProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.build();
	}

	@Override
	public boolean verify(String token, String remoteIp) {
		if (token == null || token.isBlank()) {
			return false;
		}

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("secret", properties.getSecretKey());
		form.add("response", token);
		if (remoteIp != null && !remoteIp.isBlank()) {
			form.add("remoteip", remoteIp);
		}

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = restClient.post()
					.uri(properties.getVerifyUrl())
					.contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(Map.class);

			if (body == null) {
				return false;
			}

			return Boolean.TRUE.equals(body.get("success"));
		} catch (RuntimeException e) {
			// Cloudflare being unreachable must not become an outage of the
			// submission endpoint, but it must not let a submission through
			// either.
			log.warn("Turnstile verification failed: {}", e.getMessage());
			return false;
		}
	}
}
