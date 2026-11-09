---
name: Prune service, inbox index migration, and chart lazy-load work
description: Context behind the 2026-11-09 uncommitted work — scheduled token/Stripe prune, Flyway V4 trigram+covering indexes, recharts lazy-load split
type: project
---

Three loosely-related efforts were bundled into one uncommitted working-tree change (reviewed 2026-11-09):

1. **Scheduled cleanup** (`backend/.../common/{PruneService,ScheduledCleanup,TransactionalPruneService}.java`). Daily 03:17 cron prunes expired refresh tokens, revoked refresh tokens, email-verification and password-reset tokens, and processed Stripe events (7-day retention). `@EnableScheduling` was added to `TestimonialApplication`.
2. **Flyway V4** (`V4__inbox_search_and_count_indexes.sql`): GIN trigram index on `lower(name)||' '||lower(email)||' '||lower(message)`, plus covering index `(space_id, favorite, wall_of_love, hidden, rating)`.
3. **Recharts lazy-load split**: `submissions-chart.tsx` (shell) + `submissions-chart-plot.tsx` (recharts, `next/dynamic` with `ssr:false`).

**Why:** The prune exists because five tables only ever grew; V4 exists because the inbox search was a 86ms table scan and the page count was 75ms; the chart split because recharts was ~105KB gzipped on the dashboard's critical path.

**How to apply:** The V4 trigram index is coupled to the *exact* string in `TestimonialRepository.findInbox`'s value query AND its `countQuery` — the two must stay identical or the planner silently falls back to a scan. If the search expression is ever changed, V4 must be changed in the same commit (and V4's checksum edited only via `flyway repair`, never by hand-editing an already-applied migration).
