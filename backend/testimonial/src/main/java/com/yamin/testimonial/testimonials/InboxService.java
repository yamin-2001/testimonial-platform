package com.yamin.testimonial.testimonials;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yamin.testimonial.common.exception.ConflictException;
import com.yamin.testimonial.common.exception.NotFoundException;
import com.yamin.testimonial.common.exception.ValidationException;
import com.yamin.testimonial.spaces.Space;
import com.yamin.testimonial.spaces.SpaceService;

/**
 * The owner's inbox for a Space's testimonials (PRD 4.7).
 *
 * <p>Every operation is scoped to the signed-in user, so one user can never
 * read or change another user's testimonials (PRD 6.7). A testimonial in
 * somebody else's Space is a 404 rather than a 403, so the response does not
 * reveal that it exists (PRD 8).
 *
 * <p>There is no approval step: a testimonial is visible from the moment it
 * arrives, is not on the Wall of Love, and is not a favourite (PRD 4.7).
 * Those are the owner's to change.
 */
@Service
public class InboxService {

	/** The longest a testimonial's text may be (PRD 4.5). */
	public static final int MESSAGE_MAX_LENGTH = 2000;

	/** The longest a name may be. */
	private static final int NAME_MAX_LENGTH = 200;

	/** The longest an email may be. */
	private static final int EMAIL_MAX_LENGTH = 255;

	private final TestimonialRepository testimonialRepository;

	private final SpaceService spaceService;

	public InboxService(TestimonialRepository testimonialRepository, SpaceService spaceService) {
		this.testimonialRepository = testimonialRepository;
		this.spaceService = spaceService;
	}

	/**
	 * The Space's inbox, filtered and searched.
	 *
	 * <p>Favourites come first, then newest first (PRD 4.7). Hidden
	 * testimonials stay in the inbox, marked hidden.
	 */
	@Transactional(readOnly = true)
	public Page<Testimonial> list(UUID ownerId, UUID spaceId, String term, Boolean favorite,
			Boolean wallOfLove, Boolean hidden, Short rating, Pageable pageable) {

		assertOwner(ownerId, spaceId);

		return testimonialRepository.findInbox(spaceId, blankToNull(term), favorite, wallOfLove,
				hidden, rating, pageable);
	}

	/**
	 * Marks a testimonial as a favourite, or unmarks it.
	 *
	 * <p>A favourite sorts above every non-favourite in the inbox (PRD 4.7).
	 */
	@Transactional
	public Testimonial setFavorite(UUID ownerId, UUID testimonialId, boolean favorite) {
		Testimonial testimonial = get(ownerId, testimonialId);
		assertMutable(ownerId, testimonial.getSpace());

		testimonial.setFavorite(favorite);
		return testimonialRepository.save(testimonial);
	}

	/**
	 * Adds a testimonial to the Wall of Love, or takes it off.
	 *
	 * <p>This is always an explicit action by the owner, whatever the
	 * submitter's consent said (PRD 4.7, 6.9).
	 */
	@Transactional
	public Testimonial setWallOfLove(UUID ownerId, UUID testimonialId, boolean wallOfLove) {
		Testimonial testimonial = get(ownerId, testimonialId);
		assertMutable(ownerId, testimonial.getSpace());

		testimonial.setWallOfLove(wallOfLove);
		return testimonialRepository.save(testimonial);
	}

	/**
	 * Hides a testimonial from the public pages, or shows it again.
	 *
	 * <p>A hidden testimonial stays in the owner's inbox, marked hidden, but
	 * never appears publicly (PRD 4.7, 6.11).
	 */
	@Transactional
	public Testimonial setHidden(UUID ownerId, UUID testimonialId, boolean hidden) {
		Testimonial testimonial = get(ownerId, testimonialId);
		assertMutable(ownerId, testimonial.getSpace());

		testimonial.setHidden(hidden);
		return testimonialRepository.save(testimonial);
	}

	/**
	 * Edits a testimonial's details.
	 *
	 * <p>Only the fields that are sent are changed, so an edit of one field
	 * leaves the others alone. Editing the text is reflected in embeds on the
	 * next load (PRD 4.7).
	 */
	@Transactional
	public Testimonial edit(UUID ownerId, UUID testimonialId, String name, String email,
			String message, Integer rating) {

		Testimonial testimonial = get(ownerId, testimonialId);
		assertMutable(ownerId, testimonial.getSpace());

		Map<String, String> errors = new LinkedHashMap<>();

		if (name != null) {
			String trimmed = name.trim();
			if (trimmed.isEmpty()) {
				errors.put("name", "Your name is required");
			} else if (trimmed.length() > NAME_MAX_LENGTH) {
				errors.put("name", "Your name is too long");
			} else {
				testimonial.setName(trimmed);
			}
		}

		if (email != null) {
			String trimmed = email.trim();
			if (trimmed.isEmpty()) {
				errors.put("email", "Your email is required");
			} else if (trimmed.length() > EMAIL_MAX_LENGTH) {
				errors.put("email", "Your email is too long");
			} else if (!looksLikeEmail(trimmed)) {
				errors.put("email", "Please give a valid email address");
			} else {
				testimonial.setEmail(trimmed);
			}
		}

		if (message != null) {
			String trimmed = message.trim();
			if (trimmed.isEmpty()) {
				errors.put("message", "Your testimonial is required");
			} else if (trimmed.length() > MESSAGE_MAX_LENGTH) {
				errors.put("message", "Your testimonial must be at most 2000 characters");
			} else {
				testimonial.setMessage(trimmed);
			}
		}

		if (rating != null) {
			if (rating < 1 || rating > 5) {
				errors.put("rating", "Choose a rating between 1 and 5");
			} else {
				testimonial.setRating(rating.shortValue());
			}
		}

		if (!errors.isEmpty()) {
			throw new ValidationException("Please correct the highlighted fields", errors);
		}

		return testimonialRepository.save(testimonial);
	}

	/**
	 * Deletes a testimonial for good.
	 *
	 * <p>The deletion is permanent: the row is gone, and it is excluded from
	 * every count from then on (PRD 4.7).
	 */
	@Transactional
	public void delete(UUID ownerId, UUID testimonialId) {
		Testimonial testimonial = get(ownerId, testimonialId);
		assertMutable(ownerId, testimonial.getSpace());

		testimonialRepository.delete(testimonial);
	}

	/**
	 * One of the user's testimonials.
	 *
	 * @throws NotFoundException if the testimonial does not exist or is not
	 *         the signed-in user's
	 */
	@Transactional(readOnly = true)
	public Testimonial get(UUID ownerId, UUID testimonialId) {
		return testimonialRepository.findByIdAndOwnerId(testimonialId, ownerId)
				.orElseThrow(() -> new NotFoundException("Testimonial not found"));
	}

	/**
	 * The testimonials of a Space, newest first.
	 *
	 * <p>Used where a whole Space's testimonials are needed rather than one
	 * page of them.
	 */
	@Transactional(readOnly = true)
	public List<Testimonial> listAll(UUID ownerId, UUID spaceId) {
		assertOwner(ownerId, spaceId);

		return testimonialRepository.findInbox(spaceId, null, null, null, null, null,
				Pageable.unpaged()).getContent();
	}

	/**
	 * Checks that the Space belongs to the signed-in user.
	 *
	 * <p>Answering 404 for somebody else's Space is what keeps one user from
	 * reading another user's testimonials (PRD 6.7, 8).
	 */
	private void assertOwner(UUID ownerId, UUID spaceId) {
		spaceService.get(ownerId, spaceId);
	}

	/**
	 * Rejects a change to a Space that is read-only (PRD 4.9).
	 *
	 * <p>In a read-only Space the inbox is still viewable, but every mutating
	 * action is refused -- by the backend, not only by the UI (PRD 4.7).
	 */
	private void assertMutable(UUID ownerId, Space space) {
		if (spaceService.isReadOnly(ownerId, space)) {
			throw new ConflictException(
					"This Space is read-only because you are on the Free plan with more Spaces than the limit allows");
		}
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	/**
	 * Whether a value looks like an email address.
	 *
	 * <p>The same deliberately loose check the submission endpoint uses, so an
	 * address the platform accepted on submission is not rejected on edit.
	 */
	private boolean looksLikeEmail(String email) {
		return email.indexOf('@') > 0
				&& email.indexOf('@') == email.lastIndexOf('@')
				&& email.indexOf('@') < email.length() - 1
				&& !email.contains(" ")
				&& !email.contains("\t");
	}
}
