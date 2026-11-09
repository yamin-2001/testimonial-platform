# CLAUDE.md

Testimonial Collection SaaS — a Java/Spring Boot backend plus a Next.js frontend.
Product requirements live in `docs/PRD-testimonial-saas.md`; section references
below (PRD 4.3, PRD 6, …) point into it. Read the relevant PRD section before
changing behaviour.

## Layout

```
backend/testimonial/     Spring Boot 4.1 API (modular monolith, package by feature)
frontend/testimonial/    Next.js 16 App Router app
docs/                    Product requirements
docker-compose.yml       Local PostgreSQL 17
```

Backend packages under `com.yamin.testimonial`: `auth`, `spaces`, `testimonials`,
`embed`, `billing`, `analytics`, `common`.

## Commands

### Setup

```bash
docker compose up -d                                    # PostgreSQL on :5432
cd backend/testimonial && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend/testimonial && npm install && npm run dev
```

Backend on `http://localhost:8080` (Swagger at `/swagger-ui.html`),
frontend on `http://localhost:3000`.

Copy `.env.example` to `.env` (backend) or `.env.local` (frontend) to override
defaults. Secrets are environment variables only — never commit them.

### Tests

```bash
cd backend/testimonial && ./mvnw test                   # unit + Testcontainers integration
cd frontend/testimonial && npm test                    # vitest unit
cd frontend/testimonial && npm run typecheck           # tsc --noEmit
cd frontend/testimonial && npm run lint                # eslint
cd frontend/testimonial && npm run test:e2e            # playwright
```

### Running e2e reliably

The Playwright suite drives the real backend, so **both servers must be
running**, and two settings make it reliable:

```bash
# Frontend: production build, not `next dev` (Turbopack grows to 9+ GB and
# starves the parallel browser workers, which looks like an app bug).
cd frontend/testimonial && npm run build && npm run start

# Backend: a webhook secret and a raised rate limit. The suite shares one IP,
# so the per-IP submission limiter must not refuse the later specs.
cd backend/testimonial
STRIPE_WEBHOOK_SECRET=whsec_e2e_test_secret \
SUBMISSION_RATE_LIMIT_CAPACITY=100000 \
SUBMISSION_RATE_LIMIT_REFILL_SECONDS=1 \
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

No Stripe account is needed — the webhook endpoint verifies the signature itself
and the test payloads carry everything the application uses.

## Backend rules

### Schema

Flyway owns the schema; Hibernate only validates it (`ddl-auto: validate`).
Any mismatch between a migration and the JPA mappings **prevents the app from
booting**. Add a `V<n>__<name>.sql` in `src/main/resources/db/migration` and
change the entity in the same commit.

`spring.flyway.baseline-on-migrate` is `false` deliberately. With it `true`,
Flyway silently baselines a non-empty schema, skips the migrations, and leaves
tables missing — a confusing `ddl-auto: validate` failure.

### Hibernate 7 / Boot 4 type mappings

These were established empirically against real Postgres, not from the
Hibernate 6 docs. Match them exactly:

| Postgres type | Java mapping |
|---|---|
| `smallint` | `Short` (a plain `Integer` expects `integer` and fails validation) |
| `text` | `String` + `@Column(columnDefinition = "text")` — **no `@Lob`** (`@Lob` expects Postgres `oid`) |
| `jsonb` | `@JdbcTypeCode(SqlTypes.JSON)` (never `text[]` — Boot 4 resolves `List<String>` to a JSON JDBC type) |
| `inet` | `InetAddress` + `@JdbcTypeCode(SqlTypes.INET)` |
| enum | `@Enumerated(EnumType.STRING)` — the default is ORDINAL and validation fails |
| `timestamptz` | `Instant` (correct here; `hibernate.time_zone: UTC` is set) |

`jsonb`, `inet` and `uuid` are Postgres built-ins — no `pgcrypto`/`uuid-ossp`.
`gen_random_uuid()` is built-in since PG 13.

Jackson 3 (`tools.jackson`) is on the classpath alongside Jackson 2. Custom
serializers must import `tools.jackson.databind`, not
`com.fasterxml.jackson.databind`.

### Data model decisions

- `form_config`, `embed_config` and `public_profile_fields` on a Space are JSONB.
- `public_profile_fields` is deliberately **separate** from `form_config`:
  asking for a field and publishing it are different decisions.
- Read-only Space state after a downgrade is **derived at read time**, never
  persisted.
- Ranking uses a monotonic `spaces.seq` identity column, not `created_at` —
  `created_at` alone permits microsecond ties and Postgres' sort is not stable,
  so the read-only set would differ between requests.
- `plan` and `status` live on `subscriptions` separately: a past-due account
  keeps Premium until the final payment failure. `Subscription.isPremium()` is
  the only place the effective plan is computed.
- Email uniqueness is a unique index on `lower(email)`; the address is stored
  as typed.
- Free plan caps at 3 Spaces. To reproduce a downgraded user in a test, write
  the 4th Space straight to the repository (`spaceDirectly(...)`).

### Security, CORS and auth

- Stateless API: a short-lived access JWT plus a rotating refresh token, both
  in HttpOnly cookies. No server session, so CSRF is disabled — the cookie is
  `SameSite=Lax` and the API is same-origin through the frontend's rewrite.
- Unauthenticated requests to protected endpoints return **401**, not a
  redirect. A 401 from a brand-new endpoint means "the backend is not running
  the new code", not "not authorised" (the security filter runs before routing).
- `permitAll()` is **not** enough for an endpoint fetched from another origin.
  The browser blocks the response and the widget silently renders nothing.
  Public cross-origin data → `allowedOriginPatterns("*")` with
  `allowCredentials(false)`; credentialed → an explicit origin list, never `*`.
- The rate-limit key is `X-Forwarded-For`'s leftmost entry **only** when
  `app.submission.trust-proxy` is on; otherwise it is `request.getRemoteAddr()`.
  Trusting the header without a proxy in front would let a client forge its key.
- `RateLimiter.reset()` exists only for tests. Call it in `@BeforeEach` of any
  test class that submits, or the shared IP throttles the suite.

### Spring Boot 4 test conventions

- `TestRestTemplate` lives in `org.springframework.boot.resttestclient`, not
  `org.springframework.boot.test.web.client`. It needs `spring-boot-restclient`
  on the classpath for `RestTemplateBuilder` (Boot 4's
  `spring-boot-starter-webmvc-test` does not bring it) and
  `@AutoConfigureTestRestTemplate`.
- `TestRestTemplate` has no cookie jar — turn a session cookie back into a
  `Cookie` request header by hand.
- Copy the setup from
  `src/test/java/com/yamin/testimonial/auth/web/AuthControllerTest.java` when
  adding HTTP-level integration tests.
- `PostgresTestContainer` is a **manually started static singleton**, not a
  `@Container` field. With `@Container` on a static field in an abstract base
  class, each subclass gets its own container and Testcontainers stops it when
  that class's context closes — later classes then fail with
  `Connection to localhost:<port> refused`. Never register it as a Spring
  `@Bean` either: the context would overwrite the JDBC URL that
  `@DynamicPropertySource` published. Extend the class instead of declaring a
  new container.
- `docker exec -i` is required to pipe a heredoc into `psql` — without `-i` the
  heredoc is silently dropped and the insert reports success while inserting
  nothing.

## Frontend rules

- **Next.js 16 is not the Next.js you know.** Breaking changes in APIs,
  conventions and file structure. Read the relevant guide in
  `node_modules/next/dist/docs/` before writing code.
- `frontend/testimonial/AGENTS.md` holds a generated `nextjs-agent-rules` block.
  `next dev` re-adds it — commit it with your work rather than removing it, or
  the tree stays dirty.
- API calls go through `lib/api.ts`: browser requests use a relative `/api/...`
  path (rewritten to the backend by `next.config.ts`) and server-side calls use
  `SERVER_API_BASE_URL`, because Node's `fetch` needs an absolute URL. Always
  send `credentials: "include"`.
- The backend is the sole authority for authentication, plan limits and data
  isolation. Don't reimplement those rules on the client.
- Frontend and backend must agree on the same rules, and they drift silently.
  `FormDefinitionTest` (Java) and `lib/form-fields.test.ts` (TS) are a mirrored
  pair — change both, and test the default case on each side.
- Validation messages come from the backend's field-level errors
  (`ApiError.fieldErrors`); surface them per field rather than as a banner.

### React forms

- **Never render a live `<form>` inside another `<form>`.** The browser
  validates every control in the document against its own form, so a preview's
  empty `required` inputs make the parent's `checkValidity()` false and the
  parent silently refuses to submit. Previews must be inert: no `required`,
  `readOnly`, `tabIndex={-1}`. Give previews a `data-testid` so e2e tests can
  scope to them instead of counting inputs in the whole form.
- **One submit button per stepped form.** Do not render "Continue" and "Create"
  as two branches of a conditional over the same DOM position — React reuses the
  node, and the browser reads the button's `type` *after* the click handler runs,
  so a `type="button"` that becomes `type="submit"` submits the form on the
  click that was meant to advance the step. Render a single `type="submit"`
  button and branch inside the handler.
- Charts (recharts) don't inherit Tailwind classes. Use CSS custom properties
  defined in `app/globals.css` (e.g. `--chart-ink`, `--chart-grid`) with a
  `prefers-color-scheme: dark` block; `--color-zinc-900` and friends are **not**
  defined here. A `<stop>` inside `<defs>` doesn't inherit `currentColor` — use
  `var(--chart-ink)`. Set `isAnimationActive={false}` so the plot is drawn
  immediately. When a chart looks wrong, measure the DOM (the path's `d`, the
  `clipPath` rect, the computed `stopColor`, the bounding box) before guessing.

## Testing rules

- Assert the **exact value** that proves the claim, not that an element exists.
  A test that claims something was *not* stored must assert the count is
  exactly `0`, not that a label is visible. Prefer a structural locator (the
  `dd` sibling of a `dt`) over a loose text match.
- A green e2e suite is evidence that the *assertions* pass, not that the
  feature works. After a feature's e2e suite is green, verify the claim once at
  the data layer (a `SELECT` against the real database).
- For a cross-origin feature, load the snippet into a page on a **different**
  origin (`page.setContent()` gives one). A same-origin test will never catch a
  missing CORS rule.
- Wait for React hydration before interacting with a form. A form that has not
  hydrated submits natively as a GET, so the values land in the query string
  (`/s/slug?name=Ada&email=...`) and the page never posts; a *controlled* input
  filled before hydration sets the DOM value but never reaches React's state.
  Use the helper in `e2e/spaces.spec.ts` — it looks for a `__reactProps$` key on
  the form element.

### When an e2e test fails intermittently

Check these four before touching application code — each one looks exactly like
an app bug in the failure output:

1. **Stale backend.** The dev backend keeps serving the code it was started
   with. Restart it after compiling.
2. **The shared-IP rate limiter.** A drained bucket refuses requests for a long
   time (one token per ten seconds by default). Symptom: `429 Too many
   submissions` and the visitor never reaches the thank-you page. Run the dev
   backend with a large capacity and a fast refill, and restart it to get a fresh
   bucket — buckets are built on first use and cached per IP for an hour.
3. **A click before hydration** — see above.
4. **The dev server out of memory.** `next dev` grows past 9 GB and requests
   then time out at Playwright's 5-second default, which looks like a failing
   sign-in. Run the suite against a production build.
