package com.yamin.testimonial.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the transactional emails the product needs: address verification and
 * password reset (PRD 4.1).
 *
 * <p>When no SMTP host is configured -- the usual case in local development
 * and in tests -- the message is written to the log instead, including the
 * link. That keeps the flows usable end to end without a mail server.
 */
@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final JavaMailSender mailSender;

	private final String fromAddress;

	private final boolean smtpConfigured;

	public MailService(JavaMailSender mailSender,
			@Value("${app.mail.from-address:no-reply@example.com}") String fromAddress,
			@Value("${app.mail.smtp-configured:false}") boolean smtpConfigured) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
		this.smtpConfigured = smtpConfigured;
	}

	/** Sends the link that verifies an email address. */
	public void sendVerificationEmail(String email, String name, String verificationUrl) {
		send(email, "Verify your email address",
				"Hi " + nullSafe(name) + ",\n\n"
						+ "Please confirm your email address to finish setting up your account.\n\n"
						+ verificationUrl + "\n\n"
						+ "If you did not create an account, you can ignore this message.");
	}

	/** Sends the link that resets a password. */
	public void sendPasswordResetEmail(String email, String name, String resetUrl) {
		send(email, "Reset your password",
				"Hi " + nullSafe(name) + ",\n\n"
						+ "Use the link below to choose a new password. It can be used once and "
						+ "expires shortly.\n\n"
						+ resetUrl + "\n\n"
						+ "If you did not ask for a password reset, you can ignore this message.");
	}

	private void send(String to, String subject, String body) {
		if (!smtpConfigured) {
			log.info("""
					[mail] No SMTP server configured, writing the message to the log instead.
					[mail] To:      {}
					[mail] Subject: {}
					[mail] {}
					""", to, subject, body);
			return;
		}

		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(fromAddress);
			message.setTo(to);
			message.setSubject(subject);
			message.setText(body);
			mailSender.send(message);
		}
		catch (MailException e) {
			// A failed email must not fail the request that triggered it: the
			// user can always request another link.
			log.warn("Could not send email to {}", to, e);
		}
	}

	private String nullSafe(String name) {
		return name == null || name.isBlank() ? "there" : name;
	}
}
