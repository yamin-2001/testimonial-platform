package com.yamin.testimonial.auth.appuser;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.auth.AppUser;
import com.yamin.testimonial.auth.AppUserRepository;
import com.yamin.testimonial.auth.web.dto.AuthDtos.CurrentUserResponse;

/**
 * Looks up the signed-in user.
 *
 * <p>Kept separate from {@code AuthService} so that the web layer can
 * describe the current user without holding credentials-related logic.
 */
@Service
public class AppUserLookup {

	private final AppUserRepository appUserRepository;

	public AppUserLookup(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	/**
	 * Describes the user with the given id.
	 *
	 * @throws org.springframework.security.access.AccessDeniedException if no
	 *         such user exists, or if there is no signed-in user at all
	 */
	@Transactional(readOnly = true)
	public CurrentUserResponse describe(UUID userId) {
		if (userId == null) {
			throw new org.springframework.security.access.AccessDeniedException("No such user");
		}

		AppUser user = appUserRepository.findById(userId)
				.orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
						"No such user"));
		return new CurrentUserResponse(
				user.getId().toString(),
				user.getName(),
				user.getEmail(),
				user.isEmailVerified());
	}

	/**
	 * The id of the user a token pair belongs to.
	 *
	 * <p>The subject is carried in the access token, so it is read from there
	 * rather than by inspecting the database.
	 */
	public UUID idOf(com.yamin.testimonial.auth.token.TokenPair pair) {
		return pair.userId();
	}
}
