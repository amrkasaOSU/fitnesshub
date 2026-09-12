# FitnessHub

**Train smarter. Track everything. Get coached.**

A full-stack fitness coaching platform: workout tracking with progressive-overload
analytics, nutrition/weight/step tracking, coach-client messaging and check-ins,
a data-grounded AI training assistant, and Stripe-based subscriptions.

> **On the AI assistant and Stripe:** both sit behind provider abstractions
> that degrade gracefully. With no `OPENAI_API_KEY` set, the AI endpoints
> return a clear "not configured" message rather than erroring, and every
> other feature works untouched - so the app runs fully without any paid
> third-party account. Add a key to `.env` to switch the assistant on; any
> OpenAI-compatible endpoint works via `OPENAI_BASE_URL`. Same story for
> Stripe. See [docs/ai.md](docs/ai.md).

- **Backend:** Java 21, Spring Boot 3, Spring Security (session/cookie auth),
  Spring Data JPA, PostgreSQL, Redis, Flyway, Maven.
- **Frontend:** Next.js (App Router), TypeScript, Tailwind CSS, shadcn/ui,
  TanStack Query, React Hook Form + Zod, Recharts.
- **Architecture:** Next.js frontend → Spring Boot REST API → PostgreSQL, with
  Redis for sessions/rate limiting/caching. Modular monolith, not microservices.

## Screenshots

| Coach dashboard | Client roster |
|---|---|
| ![Coach dashboard](docs/screenshots/coach-dashboard.png) | ![Client roster](docs/screenshots/coach-clients.png) |
| Attention flags surface which clients need a nudge, with recent PRs alongside. | Searchable roster with weight trend, adherence, and per-client flag counts. |

| Client detail | Today's workout |
|---|---|
| ![Client detail](docs/screenshots/client-detail.png) | ![Today's workout](docs/screenshots/workout-today.png) |
| Current weight beside target, plus 30-day training summary. | Monday-Sunday week strip, per-set logging, and skip-with-reason. |

| Client dashboard | Landing page |
|---|---|
| ![Client dashboard](docs/screenshots/client-dashboard.png) | ![Landing page](docs/screenshots/landing.png) |
| Calories, protein, steps, weight, and today's session at a glance. | Public marketing page. |

All screenshots are of the running application against the seeded demo data.

## Quick start (Docker)

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- API docs (Swagger UI): http://localhost:8080/swagger-ui.html

The backend seeds realistic demo data on first boot against an empty database
(see **Demo accounts** below) - no manual setup needed.

## Demo accounts

All demo accounts use the password `FitnessHub!2024`.

| Role   | Email                        | Story |
|--------|-------------------------------|-------|
| Coach  | coach@fitnesshub.local        | Manages all 5 clients below |
| Client | client1@fitnesshub.local      | Strength goal, bench press progressing well, high adherence, several PRs |
| Client | client2@fitnesshub.local      | Fat loss goal, weight trending down, steps improving |
| Client | client3@fitnesshub.local      | Strength plateau, high RPE, inconsistent nutrition logging - trips several attention flags |
| Client | client4@fitnesshub.local      | Inconsistent workout logging, low adherence |
| Client | client5@fitnesshub.local      | Brand-new client, almost no history |

Client 1's bench press history is built to match the "should I attempt 225
today?" scenario from the product spec: the AI assistant answers that question
from real logged sets, not a hard-coded response - see [docs/ai.md](docs/ai.md).

## Local development (without Docker)

You need JDK 21, Maven, Node 20+, PostgreSQL 16, and Redis 7 on your machine.

```bash
# Backend
cd backend
./mvnw spring-boot:run
# reads DATABASE_URL / POSTGRES_USER / POSTGRES_PASSWORD / REDIS_URL from the
# environment (see .env.example), defaulting to localhost.

# Frontend (separate terminal)
cd frontend
npm install
npm run dev
```

See [docs/development.md](docs/development.md) for the full local setup,
including how this was verified without Docker during initial development.

## Documentation

- [docs/architecture.md](docs/architecture.md) - system architecture, module layout
- [docs/database.md](docs/database.md) - schema, entity relationships, key design decisions
- [docs/api.md](docs/api.md) - REST API conventions and endpoint index
- [docs/security.md](docs/security.md) - auth, authorization, session/CSRF model
- [docs/ai.md](docs/ai.md) - AI assistant pipeline, provider abstraction, safety
- [docs/fitness-calculations.md](docs/fitness-calculations.md) - every formula (1RM, volume, adherence, calorie estimates, attention flags) and what it assumes
- [docs/development.md](docs/development.md) - local dev, testing, seed data

## Testing

```bash
cd backend
./mvnw test          # unit tests (pure logic - no DB required)
# Integration tests using Testcontainers require Docker; see docs/development.md.
```

```bash
cd frontend
npm run typecheck
npm run lint
npm run build
```

## Known limitations

See the "Known limitations" section at the end of the final delivery summary,
and [docs/development.md](docs/development.md#known-limitations) - most
notably: Testcontainers-based backend integration tests and Playwright E2E
tests are written into the plan but not included in this pass; the AI
assistant requires an `OPENAI_API_KEY` to produce real answers (it degrades
gracefully without one); Stripe billing requires real test-mode keys to
exercise end-to-end.

## Disclaimer

FitnessHub is a fitness tracking and coaching tool. Information provided by
the application or AI assistant is for general informational purposes and is
not medical advice. Consult an appropriately qualified healthcare professional
for medical concerns, injuries, or health conditions.
