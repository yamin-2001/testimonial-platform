package com.yamin.testimonial.testimonials;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to testimonials.
 *
 * <p>Two visibility rules drive almost every query here:
 * <ul>
 *   <li>Hidden testimonials stay in the owner's inbox but never appear
 *       publicly (PRD 4.7, 6.11).</li>
 *   <li>The embed shows only testimonials that are on the Wall of Love
 *       <em>and</em> visible (PRD 4.8, 6.10).</li>
 * </ul>
 *
 * <p>Both predicates are expressed here so that no caller can forget them.
 */
public interface TestimonialRepository extends JpaRepository<Testimonial, UUID> {

	/**
	 * The testimonials an embed should display: on the Wall of Love and not
	 * hidden.
	 *
	 * <p>Unauthenticated and cacheable (PRD 4.8). Backed by the partial index
	 * {@code ix_testimonials_embed}.
	 */
	@Query("""
			select t
			from Testimonial t
			where t.space.id = :spaceId
			  and t.wallOfLove = true
			  and t.hidden = false
			order by t.createdAt desc
			""")
	List<Testimonial> findEmbeddableBySpaceId(@Param("spaceId") UUID spaceId);

	/**
	 * The Space's inbox: favorites first, then newest first (PRD 4.7).
	 *
	 * <p>Hidden testimonials are included -- they stay in the inbox, marked
	 * hidden. The ordering puts favorites above non-favorites regardless of
	 * date.
	 */
	@Query("""
			select t
			from Testimonial t
			where t.space.id = :spaceId
			order by t.favorite desc, t.createdAt desc
			""")
	Page<Testimonial> findInboxBySpaceId(@Param("spaceId") UUID spaceId, Pageable pageable);

	/**
	 * Searches the inbox by name, email or testimonial text (PRD 4.7).
	 */
	@Query("""
			select t
			from Testimonial t
			where t.space.id = :spaceId
			  and (lower(t.name) like lower(concat('%', :term, '%'))
			    or lower(t.email) like lower(concat('%', :term, '%'))
			    or lower(t.message) like lower(concat('%', :term, '%')))
			order by t.favorite desc, t.createdAt desc
			""")
	Page<Testimonial> searchInbox(@Param("spaceId") UUID spaceId, @Param("term") String term,
			Pageable pageable);

	/** Finds a testimonial within a Space, scoped to the Space's owner. */
	@Query("""
			select t
			from Testimonial t
			where t.id = :id and t.space.id = :spaceId
			""")
	java.util.Optional<Testimonial> findByIdAndSpaceId(@Param("id") UUID id,
			@Param("spaceId") UUID spaceId);

	/** Counts a Space's testimonials, used to enforce the per-Space limit (PRD 4.9). */
	long countBySpaceId(UUID spaceId);

	/**
	 * Counts every testimonial across a user's Spaces.
	 *
	 * <p>Used for the dashboard's total (PRD 4.6). Deleted testimonials are
	 * gone from the table entirely, so they are excluded automatically.
	 */
	long countBySpaceOwnerId(UUID ownerId);

	/**
	 * Counts distinct submitter emails across a user's Spaces.
	 *
	 * <p>Unique submitters for the dashboard (PRD 4.6). Distinct emails are
	 * compared case-insensitively, matching the platform's email rules.
	 */
	@Query("""
			select count(distinct lower(t.email))
			from Testimonial t
			where t.space.owner.id = :ownerId
			""")
	long countDistinctSubmittersByOwnerId(@Param("ownerId") UUID ownerId);

	/**
	 * Daily submission counts for a user's Spaces, bucketed in UTC
	 * (PRD 4.6).
	 *
	 * <p>Bucketing happens in the database so that empty days can be filled in
	 * with zero by the caller.
	 */
	@Query(value = """
			select date_trunc('day', t.created_at at time zone 'UTC') as bucket, count(t)
			from testimonials t
			join spaces s on s.id = t.space_id
			where s.user_id = :ownerId
			group by bucket
			order by bucket
			""", nativeQuery = true)
	List<Object[]> countSubmissionsPerDay(@Param("ownerId") UUID ownerId);
}
