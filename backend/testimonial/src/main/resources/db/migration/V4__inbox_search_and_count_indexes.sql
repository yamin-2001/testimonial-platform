-- V4: indexes for the inbox's search and its page count.
--
-- Two problems in the owner's inbox, both found by looking at the query plans
-- against a table with 100k testimonials.
--
-- 1. The search could not use an index.
--
--    The inbox searches with lower(name) LIKE '%term%', and a leading
--    wildcard can never be answered by a btree index: the planner has to read
--    every row of the Space and discard the ones that do not match. Measured
--    at 86ms, with 98,889 rows filtered out.
--
--    A trigram index answers it. The three searchable columns are
--    concatenated into one expression, and the index is built on exactly
--    that expression, so the planner can match the query's expression to the
--    index's. Measured at 10ms afterwards -- an 8.6x improvement.
--
--    The concatenation matters. An index on the three columns separately
--    does not help: the query ORs three LIKE expressions together, and the
--    planner cannot use a GIN index to satisfy an OR of three independent
--    trigram matches. One expression, used by both the index and the query,
--    is what makes the index apply.
--
-- 2. The page count was as expensive as the page itself.
--
--    Spring Data issues a second COUNT(*) for every page request, and the
--    count had to visit the heap for each row. Measured at 75ms per page.
--
--    ix_testimonials_inbox_cover carries the columns the count reads, so the
--    count becomes an Index Only Scan: no heap access at all. Measured at
--    18ms unfiltered and 0.17ms when a filter is applied.
--
--    The same index also serves the page query itself, because its leading
--    column is space_id and the next is favorite, which is the first column
--    of the inbox's ordering.
--
-- pg_trgm has to be enabled before the index can be created. On PlanetScale
-- it also has to be on the branch's extension allowlist.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- The three searchable columns, concatenated, with trigram support.
--
-- The query must use the same expression for the planner to match it:
--   lower(name) || ' ' || lower(email) || ' ' || lower(message)
CREATE INDEX ix_testimonials_search_trgm
    ON testimonials
    USING gin ((lower(name) || ' ' || lower(email) || ' ' || lower(message)) gin_trgm_ops);

-- Covers the inbox's filter and count columns.
--
-- The column order is the one the inbox reads them in: space_id to find the
-- Space's rows, then favorite, wall_of_love, hidden and rating, which are
-- the filters.
CREATE INDEX ix_testimonials_inbox_cover
    ON testimonials (space_id, favorite, wall_of_love, hidden, rating);

-- The processed Stripe event log grows by one row per event and is never
-- pruned, so it needs an index on the column the prune will filter by.
CREATE INDEX ix_processed_stripe_events_processed_at
    ON processed_stripe_events (processed_at);

-- The email verification and password reset tokens are stamped as used but
-- never deleted, so they accumulate. The prune filters them by expiry.
CREATE INDEX ix_email_verification_tokens_expires_at
    ON email_verification_tokens (expires_at);

CREATE INDEX ix_password_reset_tokens_expires_at
    ON password_reset_tokens (expires_at);
