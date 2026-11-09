package com.yamin.testimonial.testimonials;

import java.time.Instant;
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
	 *
	 * <p>The list is capped. This endpoint is public and takes no session, so
	 * a Space with a great many testimonials must not be able to make the
	 * server build a response of unbounded size. The cap is a {@code Pageable}
	 * rather than a limit in the query, so the bound is one number held in one
	 * place.
	 */
	@Query("""
			select t
			from Testimonial t
			where t.space.id = :spaceId
			  and t.wallOfLove = true
			  and t.hidden = false
			order by t.createdAt desc
			""")
	List<Testimonial> findEmbeddableBySpaceId(@Param("spaceId") UUID spaceId, Pageable pageable);

	/**
	 * The Space's inbox, filtered and searched (PRD 4.7).
	 *
	 * <p>One query serves every combination of search and filter, because the
	 * filters are independent and the number of combinations grows with each
	 * one. Each filter is a nullable parameter: {@code null} means "do not
	 * filter on this", and the corresponding predicate is skipped.
	 *
	 * <p>The query is native because the optional filters need PostgreSQL's
	 * own null handling. Written as JPQL, a {@code null} parameter arrives
	 * untyped and PostgreSQL cannot tell whether to compare it as text, a
	 * boolean or a number.
	 *
	 * <p>The search matches the three searchable columns concatenated into one
	 * expression, and it is the same expression the trigram index
	 * {@code ix_testimonials_search_trgm} is built on. The two must be
	 * identical for the planner to use the index: an OR of three separate
	 * {@code LIKE} expressions cannot be answered by a GIN index, because the
	 * index holds one expression, not three.
	 *
	 * <p>The ordering is fixed: favourites first, then newest first. Favourites
	 * sort above non-favourites regardless of date (PRD 4.7).
	 *
	 * <p>Hidden testimonials are included unless the caller asks for them
	 * specifically. They stay in the inbox, marked hidden, and never appear
	 * publicly (PRD 4.7, 6.11).
	 *
	 * @param spaceId the Space whose inbox is wanted
	 * @param term the search term, or {@code null} for no search
	 * @param favorite {@code true} for favourites only, {@code false} for
	 *        non-favourites only, {@code null} for both
	 * @param wallOfLove {@code true} for Wall of Love only, {@code false} for
	 *        the rest, {@code null} for both
	 * @param hidden {@code true} for hidden only, {@code false} for visible
	 *        only, {@code null} for both
	 * @param rating the rating to filter by, or {@code null} for any rating
	 * @param pageable the page and sort to use
	 *
	 * <p>The count is written out rather than left to Spring Data, and it is
	 * written as {@code count(*)}. Spring Data derives a count of
	 * {@code count(t.*)}, and the difference is a change of plan, not a
	 * fraction of a millisecond. {@code count(t.*)} names every column of the
	 * row, so the planner has to consider that the count may need any of them,
	 * and it gives up on the covering index and scans the table instead.
	 * {@code count(*)} names no column at all, so the count can be answered
	 * from the index alone: on a Space with a hundred thousand testimonials it
	 * is an Index Only Scan that reads ninety buffers where the derived count
	 * reads three thousand eight hundred and fifty-five.
	 *
	 * <p>The count's predicates are the page query's predicates, so the two
	 * agree on what "the total" means. The search expression is spelled the
	 * same way in both, and it must stay that way: the trigram index holds
	 * that one expression, and an expression the planner cannot match to it
	 * sends the count back to a table scan.
	 */
	@Query(value = """
			select t.* from testimonials t
			where t.space_id = :spaceId
			  and (cast(:term as text) is null
			    or lower(t.name) || ' ' || lower(t.email) || ' ' || lower(t.message)
			       like lower('%' || cast(:term as text) || '%'))
			  and (cast(:favorite as boolean) is null or t.favorite = cast(:favorite as boolean))
			  and (cast(:wallOfLove as boolean) is null or t.wall_of_love = cast(:wallOfLove as boolean))
			  and (cast(:hidden as boolean) is null or t.hidden = cast(:hidden as boolean))
			  and (cast(:rating as smallint) is null or t.rating = cast(:rating as smallint))
			order by t.favorite desc, t.created_at desc, t.id desc
			""", countQuery = """
			select count(*) from testimonials t
			where t.space_id = :spaceId
			  and (cast(:term as text) is null
			    or lower(t.name) || ' ' || lower(t.email) || ' ' || lower(t.message)
			       like lower('%' || cast(:term as text) || '%'))
			  and (cast(:favorite as boolean) is null or t.favorite = cast(:favorite as boolean))
			  and (cast(:wallOfLove as boolean) is null or t.wall_of_love = cast(:wallOfLove as boolean))
			  and (cast(:hidden as boolean) is null or t.hidden = cast(:hidden as boolean))
			  and (cast(:rating as smallint) is null or t.rating = cast(:rating as smallint))
			""", nativeQuery = true)
	Page<Testimonial> findInbox(@Param("spaceId") UUID spaceId,
			@Param("term") String term,
			@Param("favorite") Boolean favorite,
			@Param("wallOfLove") Boolean wallOfLove,
			@Param("hidden") Boolean hidden,
			@Param("rating") Short rating,
			Pageable pageable);

	/** Finds a testimonial within a Space, scoped to the Space's owner. */
	@Query("""
			select t
			from Testimonial t
			where t.id = :id and t.space.id = :spaceId
			""")
	java.util.Optional<Testimonial> findByIdAndSpaceId(@Param("id") UUID id,
			@Param("spaceId") UUID spaceId);

	/**
	 * Finds a testimonial by id, restricted to the owner of the Space it
	 * belongs to.
	 *
	 * <p>Returning empty for a testimonial in somebody else's Space is what
	 * makes a cross-user request a 404 rather than a 403 (PRD 4.7, 8). The
	 * check is on the owner rather than on the Space id, so a caller cannot
	 * reach a testimonial by guessing its Space.
	 */
	@Query("""
			select t
			from Testimonial t
			where t.id = :id and t.space.owner.id = :ownerId
			""")
	java.util.Optional<Testimonial> findByIdAndOwnerId(@Param("id") UUID id,
			@Param("ownerId") UUID ownerId);

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
	 *
	 * <p>The range is bounded here rather than in Java, so the database only
	 * counts the days the chart will show. The lower bound is inclusive and
	 * the upper one is exclusive, so a range of thirty days covers exactly
	 * thirty buckets.
	 *
	 * <p>Hidden testimonials are counted. Hiding a testimonial takes it out of
	 * the public pages, not out of the owner's counts -- the PRD says deleted
	 * testimonials are excluded, and says nothing about hidden ones, which
	 * are still the owner's to read (PRD 4.7).
	 */
	@Query(value = """
			select date_trunc('day', t.created_at at time zone 'UTC') as bucket, count(t)
			from testimonials t
			join spaces s on s.id = t.space_id
			where s.user_id = :ownerId
			  and t.created_at >= :from
			  and t.created_at < :to
			group by bucket
			order by bucket
			""", nativeQuery = true)
	List<Object[]> countSubmissionsPerDayBetween(@Param("ownerId") UUID ownerId,
			@Param("from") Instant from, @Param("to") Instant to);

	/**
	 * The most recent submissions across a user's Spaces (PRD 4.6).
	 *
	 * <p>Newest first, across every Space the user owns rather than one
	 * Space, because the dashboard is the view of everything they have
	 * collected.
	 *
	 * <p>The Space's id, title and slug are selected along with the
	 * testimonial, rather than read from the lazy reference afterwards.
	 * {@code open-in-view} is off, so a reference touched outside the
	 * transaction would fail to initialise. The id is what the dashboard's
	 * list links to.
	 */
	@Query("""
			select new com.yamin.testimonial.testimonials.RecentSubmissionView(
				t.id, t.name, t.message, t.rating, t.space.id, t.space.title, t.space.slug, t.createdAt)
			from Testimonial t
			where t.space.owner.id = :ownerId
			order by t.createdAt desc, t.id desc
			""")
	List<RecentSubmissionView> findRecentByOwnerId(@Param("ownerId") UUID ownerId,
			Pageable pageable);
}
