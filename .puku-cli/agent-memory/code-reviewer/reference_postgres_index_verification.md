---
name: Verifying Postgres index usage and Flyway behaviour
description: Docker-based workflow to prove whether a Postgres index is actually used and whether a Flyway migration is interruption-safe
type: reference
---

To check whether a Postgres index is really used by a query, a throwaway container is faster and more conclusive than reading EXPLAIN output in the app:

```
docker run -d --name pgsql-probe -e POSTGRES_PASSWORD=probe -e POSTGRES_DB=probe postgres:17-alpine
# wait for readiness by polling `psql -c "select 1"` (the image ignores POSTGRES_DB until init finishes)
```

Two traps found the hard way on 2026-11-09:

- **A seq scan does not mean the index is unused.** With a small table or when all rows belong to one tenant, the planner correctly prefers a seq scan. Insert rows for a *second* space and `VACUUM (ANALYZE)` — the index-only scan then appears with `Heap Fetches: 0`.
- **Verify the migration as written, not a retyped version.** Apply the exact SQL from the `.sql` file. PostgreSQL normalises `lower(name)` and `lower(name::text)` to the same index expression, but retyping the SQL risks "fixing" something that was already correct.

**How to apply:** `CREATE EXTENSION` and `CREATE INDEX` are both transactional in PostgreSQL, so a Flyway migration that is interrupted rolls back cleanly and leaves no orphaned indexes. Flyway's transactional DDL makes the "interrupted migration orphans indexes" worry unfounded for this repo — confirm with `BEGIN; <migration SQL>; ROLLBACK;` rather than assuming.
