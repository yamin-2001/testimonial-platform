package com.yamin.testimonial.spaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to Spaces.
 *
 * <p>Every owner-scoped query filters on the owning user, so one user can
 * never read or modify another user's Spaces (PRD 6.7, 8).
 */
public interface SpaceRepository extends JpaRepository<Space, UUID> {

	/**
	 * Finds a Space by its public slug.
	 *
	 * <p>Slugs are unique across the platform (PRD 4.2). Used by the public
	 * submission page and the embed endpoint, both of which are
	 * unauthenticated.
	 */
	Optional<Space> findBySlug(String slug);

	/** All Spaces owned by a user, oldest first. */
	List<Space> findByOwnerIdOrderBySeqAsc(UUID ownerId);

	long countByOwnerId(UUID ownerId);

	/**
	 * Finds a Space by id, restricted to its owner.
	 *
	 * <p>Returning empty for someone else's Space is what makes a cross-user
	 * request a 404 rather than a 403 (PRD 4.2, 8).
	 */
	Optional<Space> findByIdAndOwnerId(UUID id, UUID ownerId);

	/**
	 * Whether the Space has received its first submission.
	 *
	 * <p>Drives the slug lock: once this is true the slug can no longer be
	 * changed (PRD 4.2). Derived rather than stored so it cannot drift out of
	 * sync with the testimonials that actually exist.
	 */
	@Query("""
			select count(t) > 0
			from Testimonial t
			where t.space.id = :spaceId
			""")
	boolean hasAnyTestimonials(@Param("spaceId") UUID spaceId);

	/**
	 * The number of testimonials a Space has.
	 *
	 * <p>Used to enforce the per-Space testimonial limit on submission
	 * (PRD 4.9).
	 */
	@Query("""
			select count(t)
			from Testimonial t
			where t.space.id = :spaceId
			""")
	long countTestimonials(@Param("spaceId") UUID spaceId);

	/**
	 * Finds a Space by its slug and locks the row for the rest of the
	 * transaction.
	 *
	 * <p>The lock is what makes the per-Space testimonial limit hold under
	 * concurrent submissions. Counting and then inserting without a lock is a
	 * race: two submissions can both read a count below the limit and both
	 * insert, taking the Space one over the limit (PRD 4.9).
	 *
	 * <p>Locking the Space row serialises the submissions for that Space. The
	 * lock is released when the transaction ends.
	 */
	@Query(value = "select * from spaces where slug = :slug for update", nativeQuery = true)
	Optional<Space> findBySlugForUpdate(@Param("slug") String slug);
}
