package com.yamin.testimonial.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.auth.EmailVerificationTokenRepository;
import com.yamin.testimonial.auth.PasswordResetTokenRepository;
import com.yamin.testimonial.auth.token.RefreshTokenRepository;
import com.yamin.testimonial.auth.token.RevokedRefreshTokenRepository;
import com.yamin.testimonial.support.PostgresTestContainer;
import com.yamin.testimonial.support.RecordingMailConfiguration;
import com.yamin.testimonial.support.RecordingMailSender;

/**
 * The authentication endpoints over HTTP (PRD 4.1).
 *
 * <p>The service-level behaviour is covered by {@code AuthServiceTest}. This
 * test is about the contract the frontend depends on: the status codes, the
 * error shape, and the session cookies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(RecordingMailConfiguration.class)
class AuthControllerTest extends PostgresTestContainer {

	/** The raw token in an emailed link. */
	private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([0-9a-fA-F-]{36})");

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private RevokedRefreshTokenRepository revokedRefreshTokenRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private RecordingMailSender mailSender;

	@BeforeEach
	void reset() {
		mailSender.clear();
		emailVerificationTokenRepository.deleteAll();
		passwordResetTokenRepository.deleteAll();
		revokedRefreshTokenRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		appUserRepository.deleteAll();
	}

	@Test
	@DisplayName("registering returns 201 and creates an unverified account")
	void registerReturnsCreated() {
		ResponseEntity<String> response = post("/api/auth/register",
				"{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\",\"password\":\"password123\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).contains("ada@example.com");
		assertThat(response.getBody()).contains("\"emailVerified\":false");
		assertThat(appUserRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("registering an address that already exists returns 409 and creates nothing")
	void duplicateEmailReturnsConflict() {
		post("/api/auth/register",
				"{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\",\"password\":\"password123\"}");

		ResponseEntity<String> response = post("/api/auth/register",
				"{\"name\":\"Someone Else\",\"email\":\"ada@example.com\",\"password\":\"password123\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("already exists");
		assertThat(appUserRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("an invalid registration returns 400 with a message per field")
	void invalidRegistrationReturnsFieldErrors() {
		ResponseEntity<String> response = post("/api/auth/register",
				"{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"errors\"");
		assertThat(response.getBody()).contains("email");
		assertThat(response.getBody()).contains("password");
		assertThat(appUserRepository.count()).isZero();
	}

	@Test
	@DisplayName("signing in sets both session cookies as HttpOnly")
	void loginSetsSessionCookies() {
		register();

		ResponseEntity<String> response = post("/api/auth/login",
				"{\"email\":\"ada@example.com\",\"password\":\"password123\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		assertThat(cookies).hasSize(2);
		assertThat(cookies).anySatisfy(cookie -> {
			assertThat(cookie).startsWith("session=");
			assertThat(cookie).contains("HttpOnly");
			assertThat(cookie).contains("SameSite=Lax");
		});
		assertThat(cookies).anySatisfy(cookie -> {
			assertThat(cookie).startsWith("refresh=");
			assertThat(cookie).contains("HttpOnly");
		});
	}

	@Test
	@DisplayName("the current user is returned when the session cookie is presented")
	void meReturnsTheSignedInUser() {
		register();
		HttpHeaders headers = withSessionCookies(login());

		ResponseEntity<String> response = restTemplate.exchange("/api/auth/me", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("ada@example.com");
	}

	@Test
	@DisplayName("the current user endpoint answers 401 without a session")
	void meWithoutSessionIsUnauthorized() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/auth/me", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("the current user endpoint answers 401 with a session cookie that is not valid")
	void meWithInvalidSessionIsUnauthorized() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, "session=not-a-real-token");

		ResponseEntity<String> response = restTemplate.exchange("/api/auth/me", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("a wrong password and an unknown address both answer 401 with the same message")
	void wrongCredentialsGiveTheSameGenericError() {
		register();

		ResponseEntity<String> wrongPassword = post("/api/auth/login",
				"{\"email\":\"ada@example.com\",\"password\":\"not-the-password\"}");
		ResponseEntity<String> unknownEmail = post("/api/auth/login",
				"{\"email\":\"nobody@example.com\",\"password\":\"not-the-password\"}");

		assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(wrongPassword.getBody()).isEqualTo(unknownEmail.getBody());
	}

	@Test
	@DisplayName("signing out clears the cookies and revokes the session")
	void logoutClearsCookiesAndRevokesTheSession() {
		register();
		ResponseEntity<String> login = login();

		ResponseEntity<String> response = restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
				new HttpEntity<>(withSessionCookies(login)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		assertThat(cookies).hasSize(2);
		assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));

		// The refresh token no longer works.
		HttpHeaders refreshOnly = new HttpHeaders();
		refreshOnly.add(HttpHeaders.COOKIE, "refresh=" + cookieValue(login, "refresh"));
		ResponseEntity<String> refreshed = restTemplate.exchange("/api/auth/refresh", HttpMethod.POST,
				new HttpEntity<>(refreshOnly), String.class);

		assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("refreshing issues a new session and retires the old refresh token")
	void refreshRotatesTheSession() {
		register();
		ResponseEntity<String> login = login();

		HttpHeaders refreshOnly = new HttpHeaders();
		refreshOnly.add(HttpHeaders.COOKIE, "refresh=" + cookieValue(login, "refresh"));

		ResponseEntity<String> response = restTemplate.exchange("/api/auth/refresh", HttpMethod.POST,
				new HttpEntity<>(refreshOnly), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2);

		// The old refresh token is revoked by the rotation.
		ResponseEntity<String> reused = restTemplate.exchange("/api/auth/refresh", HttpMethod.POST,
				new HttpEntity<>(refreshOnly), String.class);

		assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("a verification link works once and then is rejected")
	void verificationLinkIsSingleUse() {
		register();
		String token = tokenFrom(mailSender.verificationLinks().get(0));

		ResponseEntity<String> first = post("/api/auth/verify-email?token=" + token, null);
		ResponseEntity<String> second = post("/api/auth/verify-email?token=" + token, null);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("a password reset always answers 204, whether or not the address is registered")
	void forgotPasswordAlwaysAnswersNoContent() {
		register();

		ResponseEntity<String> known = post("/api/auth/forgot-password",
				"{\"email\":\"ada@example.com\"}");
		ResponseEntity<String> unknown = post("/api/auth/forgot-password",
				"{\"email\":\"nobody@example.com\"}");

		assertThat(known.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	@DisplayName("resetting a password lets the new password be used and the old one fail")
	void resetPasswordChangesTheCredentials() {
		register();
		post("/api/auth/forgot-password", "{\"email\":\"ada@example.com\"}");
		String token = tokenFrom(mailSender.resetLinks().get(0));

		ResponseEntity<String> response = post("/api/auth/reset-password?token=" + token,
				"{\"password\":\"newpassword123\"}");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(post("/api/auth/login",
				"{\"email\":\"ada@example.com\",\"password\":\"newpassword123\"}").getStatusCode())
				.isEqualTo(HttpStatus.OK);
		assertThat(post("/api/auth/login",
				"{\"email\":\"ada@example.com\",\"password\":\"password123\"}").getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("a used reset link is rejected")
	void resetLinkIsSingleUse() {
		register();
		post("/api/auth/forgot-password", "{\"email\":\"ada@example.com\"}");
		String token = tokenFrom(mailSender.resetLinks().get(0));

		post("/api/auth/reset-password?token=" + token, "{\"password\":\"newpassword123\"}");
		ResponseEntity<String> second = post("/api/auth/reset-password?token=" + token,
				"{\"password\":\"anotherpassword123\"}");

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	/**
	 * Registers the account used by the tests.
	 */
	private void register() {
		post("/api/auth/register",
				"{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\",\"password\":\"password123\"}");
	}

	/**
	 * Signs in and returns the response, which carries the session cookies.
	 */
	private ResponseEntity<String> login() {
		return post("/api/auth/login",
				"{\"email\":\"ada@example.com\",\"password\":\"password123\"}");
	}

	private ResponseEntity<String> post(String path, String json) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		return restTemplate.exchange(path, HttpMethod.POST,
				new HttpEntity<>(json == null ? "" : json, headers), String.class);
	}

	/**
	 * Reads one cookie's value out of a response.
	 */
	private String cookieValue(ResponseEntity<String> response, String name) {
		return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
				.filter(cookie -> cookie.startsWith(name + "="))
				.map(cookie -> cookie.substring(name.length() + 1, cookie.indexOf(';')))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no " + name + " cookie in " + response.getHeaders()));
	}

	/**
	 * The request headers that carry a response's session cookies back to the
	 * server.
	 *
	 * <p>{@code TestRestTemplate} does not keep a cookie jar, so the cookies
	 * are turned back into the {@code Cookie} header a browser would send.
	 */
	private HttpHeaders withSessionCookies(ResponseEntity<String> response) {
		HttpHeaders headers = new HttpHeaders();
		String cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
				.map(cookie -> cookie.substring(0, cookie.indexOf(';')))
				.collect(Collectors.joining("; "));
		headers.add(HttpHeaders.COOKIE, cookies);
		return headers;
	}

	/**
	 * Pulls the raw token out of an emailed link.
	 */
	private String tokenFrom(String link) {
		Matcher matcher = TOKEN_PATTERN.matcher(link);
		assertThat(matcher.find()).as("the link carries a token: %s", link).isTrue();
		return matcher.group(1);
	}
}
