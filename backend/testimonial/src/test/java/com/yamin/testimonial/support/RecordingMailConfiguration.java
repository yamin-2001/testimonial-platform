package com.yamin.testimonial.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real mail sender with one that keeps what was sent.
 *
 * <p>The verification and password-reset links only exist inside an email, so
 * the tests need to read them back. The sender is marked
 * {@link Primary} so it wins over the auto-configured one.
 */
@TestConfiguration
public class RecordingMailConfiguration {

	/**
	 * The recording sender.
	 *
	 * <p>It is itself a {@code JavaMailSender}, so this single bean replaces
	 * the auto-configured sender.
	 */
	@Bean
	@Primary
	public RecordingMailSender recordingMailSender() {
		return new RecordingMailSender();
	}
}
