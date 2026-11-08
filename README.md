# Testimonial Collection SaaS

A SaaS platform where users create testimonial collection pages ("Spaces"), share a
public link, collect testimonials from customers who don't need an account, manage
them in an inbox, and display selected ones on external websites via an embeddable
widget.

See [`docs/PRD-testimonial-saas.md`](docs/PRD-testimonial-saas.md) for the full
product requirements.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security, JPA/Hibernate, Flyway, PostgreSQL |
| Frontend | Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS v4 |
| Billing | Stripe (Checkout + webhooks) |
| Abuse protection | Cloudflare Turnstile, Bucket4j rate limiting |

## Prerequisites

- **Node.js >= 20.9.0** — Next.js 16 requires it. Check with `node -v`.
- **Java 21+** — `java -version`.
- **Docker + Docker Compose** — for the local PostgreSQL instance.

## Getting started

### 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts PostgreSQL 17 on `localhost:5432` with database `testimonial` and
credentials `testimonial` / `testimonial`. Override them via `.env` (copy
`.env.example` to `.env`).

### 2. Run the backend

```bash
cd backend/testimonial
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The API listens on `http://localhost:8080`. Flyway applies migrations from
`src/main/resources/db/migration` on startup. Swagger UI is at
`http://localhost:8080/swagger-ui.html`.

### 3. Run the frontend

```bash
cd frontend/testimonial
npm install
npm run dev
```

The app listens on `http://localhost:3000`. Copy `.env.example` to `.env.local`
if you need to point it at a different backend.

## Configuration

Secrets live in environment variables (PRD 8). Copy `.env.example` to `.env` and
fill in what you need; the backend reads `SPRING_DATASOURCE_*`, `GOOGLE_*`,
`STRIPE_*`, `TURNSTILE_*` and `MAIL_*`. The frontend reads `NEXT_PUBLIC_*`.

The Google OAuth2 client id/secret default to a placeholder so the application
context still boots without them. Set the real values to enable Google sign-in.

## Testing

```bash
# Backend
cd backend/testimonial && ./mvnw test

# Frontend unit tests
cd frontend/testimonial && npm test

# Frontend end-to-end tests (requires both servers running)
cd frontend/testimonial && npm run test:e2e
```

### Running the end-to-end tests

The browser tests drive the real backend, so both servers must be up. Two
settings make the suite reliable:

**Run the frontend as a production build.** `next dev` grows to several
gigabytes of memory over a few hours, and the suite's parallel browser workers
then run into request timeouts that look like application bugs. Build and start
it instead:

```bash
cd frontend/testimonial
npm run build && npm run start
```

**Give the backend a webhook secret and a large submission rate limit.** The
billing tests sign webhook payloads the way Stripe does, so the backend needs
the same secret. The public-submission endpoint is rate limited per IP, and
the whole suite shares one, so the limit must be raised or the later tests
will be refused:

```bash
cd backend/testimonial
STRIPE_WEBHOOK_SECRET=whsec_e2e_test_secret \
SUBMISSION_RATE_LIMIT_CAPACITY=100000 \
SUBMISSION_RATE_LIMIT_REFILL_SECONDS=1 \
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

No Stripe account is needed. The webhook endpoint verifies the signature
itself, and the event types the tests use carry everything the application
needs in the payload, so nothing calls out to Stripe.

## Project layout

```
backend/testimonial/     Spring Boot API (modular monolith)
frontend/testimonial/    Next.js app
docs/                    Product requirements
docker-compose.yml       Local PostgreSQL
```

## License

Proprietary.
