package com.yamin.testimonial.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.auth.mail.MailService;
import com.yamin.testimonial.auth.token.JwtService;
import com.yamin.testimonial.auth.token.JwtService.TokenType;
import com.yamin.testimonial.auth.token.RevokedRefreshToken;
import com.yamin.testimonial.auth.token.RevokedRefreshTokenRepository;
import com.yamin.testimonial.auth.token.TokenPair;
import com.yamin.testimonial.auth.web.dto.AuthDtos.CurrentUserResponse;
import com.yamin.testimonial.common.exception.AuthException;
import com.yamin.testimonial.common.exception.ConflictException;

import io.jsonwebtoken.Claims;

/**
 * The authentication flows of PRD 4.1.
 *
 * <p>Owns registration, sign-in, sign-out, token refresh, email verification
 * and password recovery. Sessions are stateless: an access JWT and a refresh
 * token, both carried in HttpOnly cookies.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	/** How long an emailed link remains valid (PRD 4.1). */
	private static final Duration LINK_TTL = Duration.ofHours(24);

	private static final String GENERIC_CREDENTIALS_ERROR =
			"The email or password you entered is incorrect";

	private final AppUserRepository appUserRepository;

	private final EmailVerificationTokenRepository emailVerificationTokenRepository;

	private final PasswordResetTokenRepository passwordResetTokenRepository;

	private final RevokedRefreshTokenRepository revokedRefreshTokenRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final MailService mailService;

	private final String publicBaseUrl;

	public AuthService(AppUserRepository appUserRepository,
			EmailVerificationTokenRepository emailVerificationTokenRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			RevokedRefreshTokenRepository revokedRefreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			MailService mailService,
			@Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
		this.appUserRepository = appUserRepository;
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.revokedRefreshTokenRepository = revokedRefreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.mailService = mailService;
		this.publicBaseUrl = publicBaseUrl;
	}

	/**
	 * Registers a new account.
	 *
	 * <p>Creates an unverified account and emails a verification link. If the
	 * email is already registered, nothing is created and the caller is told
	 * so clearly (PRD 4.1).
	 */
	@Transactional
	public AppUser register(String name, String email, String password) {
		if (appUserRepository.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("An account with this email address already exists");
		}

		AppUser user = new AppUser();
		user.setName(name);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setEmailVerified(false);
		appUserRepository.save(user);

		sendVerificationLink(user);
		return user;
	}

	/**
	 * Signs a user in with an email and password.
	 *
	 * <p>Wrong credentials produce one generic message, so the response does
	 * not reveal whether the email exists (PRD 4.1).
	 */
	@Transactional(readOnly = true)
	public AppUser authenticate(String email, String password) {
		Optional<AppUser> found = appUserRepository.findByEmailIgnoreCase(email);
		if (found.isEmpty()) {
			// Still run the encoder so that a missing account and a wrong
			// password take a similar amount of time.
			passwordEncoder.encode(password);
			throw new AuthException(GENERIC_CREDENTIALS_ERROR);
		}

		AppUser user = found.get();
		if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new AuthException(GENERIC_CREDENTIALS_ERROR);
		}
		return user;
	}

	/**
	 * Issues a session for a user.
	 */
	public TokenPair issueSession(UUID userId) {
		return jwtService.issueTokens(userId);
	}

	/**
	 * Exchanges a refresh token for a new pair of tokens.
	 *
	 * <p>The old refresh token is revoked as the new one is issued, so a
	 * refresh token cannot be used twice.
	 */
	@Transactional
	public TokenPair refresh(String refreshToken) {
		Claims claims = jwtService.parse(refreshToken, TokenType.REFRESH);
		if (claims == null) {
			throw new AuthException("Your session has expired, please sign in again");
		}

		String jti = claims.getId();
		if (revokedRefreshTokenRepository.existsById(jti)) {
			throw new AuthException("Your session has expired, please sign in again");
		}

		UUID userId = UUID.fromString(claims.getSubject());
		if (!appUserRepository.existsById(userId)) {
			throw new AuthException("Your session has expired, please sign in again");
		}

		revokeRefreshToken(jti, claims.getExpiration().toInstant());
		return jwtService.issueTokens(userId);
	}

	/**
	 * Ends a session by revoking its refresh token.
	 *
	 * <p>The access token cannot be revoked -- it is stateless -- but it
	 * expires within minutes, and the refresh token no longer works, so the
	 * session is over.
	 */
	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		Claims claims = jwtService.parse(refreshToken, TokenType.REFRESH);
		if (claims == null) {
			// Already invalid; nothing to revoke.
			return;
		}
		revokeRefreshToken(claims.getId(), claims.getExpiration().toInstant());
	}

	private void revokeRefreshToken(String jti, Instant expiresAt) {
		revokedRefreshTokenRepository.save(new RevokedRefreshToken(jti, expiresAt, Instant.now()));
	}

	/**
	 * Confirms an email address using the token from the emailed link.
	 *
	 * <p>A used or expired link is rejected (PRD 4.1).
	 */
	@Transactional
	public void verifyEmail(String rawToken) {
		EmailVerificationToken token = emailVerificationTokenRepository
				.findByTokenHash(hashToken(rawToken))
				.orElseThrow(() -> new AuthException("This verification link is invalid or has expired"));

		if (emailVerificationTokenRepository.consume(token.getId(), Instant.now()) == 0) {
			throw new AuthException("This verification link is invalid or has expired");
		}

		AppUser user = token.getUser();
		user.setEmailVerified(true);
		appUserRepository.save(user);
	}

	/**
	 * Starts a password reset by emailing a link.
	 *
	 * <p>The response is the same whether or not the address is registered, so
	 * this endpoint cannot be used to discover which addresses have accounts.
	 */
	@Transactional
	public void requestPasswordReset(String email) {
		appUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
			String rawToken = UUID.randomUUID().toString();

			PasswordResetToken token = new PasswordResetToken();
			token.setUser(user);
			token.setTokenHash(hashToken(rawToken));
			token.setExpiresAt(Instant.now().plus(LINK_TTL));
			passwordResetTokenRepository.save(token);

			// The link carries the raw token; only its hash is stored.
			mailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetUrl(rawToken));
		});
	}

	/**
	 * Completes a password reset using the token from the emailed link.
	 *
	 * <p>A used or expired link is rejected (PRD 4.1).
	 */
	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		PasswordResetToken token = passwordResetTokenRepository
				.findByTokenHash(hashToken(rawToken))
				.orElseThrow(() -> new AuthException("This password reset link is invalid or has expired"));

		if (passwordResetTokenRepository.consume(token.getId(), Instant.now()) == 0) {
			throw new AuthException("This password reset link is invalid or has expired");
		}

		AppUser user = token.getUser();
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		appUserRepository.save(user);

		// A password change should end any session opened with the old one.
		revokedRefreshTokenRepository.deleteAll();
	}

	/** Describes the signed-in user. */
	public CurrentUserResponse describe(AppUser user) {
		return new CurrentUserResponse(
				user.getId().toString(),
				user.getName(),
				user.getEmail(),
				user.isEmailVerified());
	}

	private void sendVerificationLink(AppUser user) {
		String rawToken = UUID.randomUUID().toString();

		EmailVerificationToken token = new EmailVerificationToken();
		token.setUser(user);
		token.setTokenHash(hashToken(rawToken));
		token.setExpiresAt(Instant.now().plus(LINK_TTL));
		emailVerificationTokenRepository.save(token);

		// The link carries the raw token; only its hash is stored.
		mailService.sendVerificationEmail(user.getEmail(), user.getName(), verificationUrl(rawToken));
	}

	private String verificationUrl(String rawToken) {
		return publicBaseUrl + "/api/auth/verify-email?token=" + rawToken;
	}

	private String resetUrl(String rawToken) {
		return publicBaseUrl + "/api/auth/reset-password?token=" + rawToken;
	}

	/**
	 * Hashes an emailed token.
	 *
	 * <p>The raw token is only ever shown in the email; the database stores
	 * its hash, so a leak of the table cannot be used to verify an address or
	 * reset a password.
	 */
	static String hashToken(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required to hash tokens", e);
		}
	}
}
