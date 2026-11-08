package com.yamin.testimonial.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Captures the emails the application sends instead of delivering them.
 *
 * <p>The verification and password-reset links only exist inside an email, so
 * a test that exercises those flows needs a way to get hold of them. This
 * extends the standard sender and keeps what was sent rather than connecting
 * to an SMTP server.
 *
 * <p>Registered as a bean in the test context, replacing the real sender.
 */
public class RecordingMailSender extends JavaMailSenderImpl {

	private final List<SimpleMailMessage> sent = new ArrayList<>();

	@Override
	public void send(SimpleMailMessage simpleMessage) throws MailException {
		sent.add(simpleMessage);
	}

	@Override
	public void send(SimpleMailMessage... simpleMessages) throws MailException {
		for (SimpleMailMessage message : simpleMessages) {
			sent.add(message);
		}
	}

	/**
	 * The bodies of every email sent so far.
	 */
	public List<String> bodies() {
		return sent.stream().map(SimpleMailMessage::getText).toList();
	}

	/**
	 * The bodies of the emails that carry a verification link.
	 */
	public List<String> verificationLinks() {
		return bodies().stream().filter(body -> body.contains("/api/auth/verify-email")).toList();
	}

	/**
	 * The bodies of the emails that carry a password-reset link.
	 */
	public List<String> resetLinks() {
		return bodies().stream().filter(body -> body.contains("/api/auth/reset-password")).toList();
	}

	/**
	 * Forgets everything sent so far.
	 */
	public void clear() {
		sent.clear();
	}
}
