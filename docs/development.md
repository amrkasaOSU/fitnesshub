# Development

## Prerequisites

- JDK 21
- Maven (or use the checked-in `./mvnw` wrapper - it downloads Maven itself)
- Node.js 20+ and npm
- PostgreSQL 16 and Redis 7 reachable at the URLs in `.env.example`

## Running the backend

```bash
cd backend
./mvnw spring-boot:run
```

Reads `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_URL` from
the environment (see `.env.example` for defaults matching `docker-compose.yml`).
Flyway migrations and the demo seed data (`SeedDataRunner`) run automatically
against an empty database. Set `SEED_ENABLED=false` to skip seeding.

## Running the frontend

```bash
cd frontend
npm install
npm run dev
```

Reads `NEXT_PUBLIC_API_URL` (defaults to `http://localhost:8080`).

## Tests

```bash
cd backend
./mvnw test
```

The unit test suite (`ProgressionServiceTest`, `WeightServiceTest`,
`AdherenceServiceTest`, and friends) is deliberately written to run with
**no database and no Spring context** - each tests a pure calculation
(1RM, volume, PR eligibility, weekly averages, adherence math) by
constructing the service directly with `null` for any collaborator the method
under test doesn't touch. That's why they run in milliseconds and don't need
Testcontainers.

**Testcontainers-based integration tests and Playwright E2E tests are a
known gap.** Both need a Docker daemon, which the machine this was built on
doesn't have (see below for how the stack was verified anyway). The seams for
them exist (repositories,
`AuthorizationService`, and the REST controllers are all plain Spring beans
a `@SpringBootTest` + Testcontainers Postgres could exercise directly) - this
is flagged as a known gap, not silently skipped.

```bash
cd frontend
npm run typecheck   # tsc --noEmit
npm run lint        # eslint
npm run build       # production build (also type-checks)
```

## Verifying without Docker

My development machine has no Docker daemon, no Homebrew, and no system
Postgres/Redis/Node/Maven. Rather than skip integration verification and hope
the code was right, I stood the whole stack up from portable, non-system
binaries:

- **Maven & Node**: official binary tarballs extracted to a local directory and
  put on `PATH` - the same thing a CI runner does on every job.
- **PostgreSQL**: the real `postgres`/`initdb`/`pg_ctl` binaries embedded in the
  `io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8` Maven
  artifact, extracted directly without pulling in the Java wrapper library.
  This is actual PostgreSQL, not an in-memory substitute like H2.
- **Redis**: built from source (`make BUILD_TLS=no`). Redis's core has no
  dependencies beyond a C compiler, so it builds in about two minutes.

Against that real Postgres and Redis, the backend boots, all Flyway migrations
apply, the seed data loads, and the auth -> dashboard -> workout-logging ->
PR-detection -> messaging -> coach-notes path runs end to end.

**This caught two real bugs that the unit tests could not:**

1. **Foreign-key ordering.** Hibernate's `order_inserts` optimization batches
   inserts by entity type, which reordered `workout_sets` ahead of the
   `workout_exercises` rows they point at. This schema uses plain UUID foreign
   keys rather than JPA `@ManyToOne` associations, so Hibernate cannot see the
   dependency and cannot know the ordering matters. Fixed by leaving
   `order_inserts`/`order_updates` at their defaults - see the comment in
   `application.yml`, which explains why they must stay off.
2. **A missing dependency.** `/actuator/health` returned 500 because
   `spring-boot-starter-actuator` was referenced in configuration and in the
   Docker healthcheck but had never been added to `pom.xml`. Compiles fine;
   fails the moment anything actually calls it.

Neither is the kind of bug a unit test with mocked collaborators would surface,
which is the argument for doing this rather than trusting green tests.

None of this local tooling ships with the application. `docker-compose.yml` is
the supported way to run the project, and CI (`.github/workflows/ci.yml`) runs
against real `postgres:16-alpine` and `redis:7-alpine` service containers.

## Seed data

`SeedDataRunner` (`com.fitnesshub.config.seed`) only runs when the `users`
table is empty, and builds:

- 1 coach, 5 clients (each with a distinct story - see the main README)
- 24 exercises across all muscle groups/equipment types
- 2 programs (a 12-week strength program, an 8-week hypertrophy program),
  each with a genuine Upper/Lower A-B split using *different* exercises on
  the A and B days (an earlier version reused the same exercise on both,
  which corrupted that exercise's progress trend by mixing two unrelated
  rep/weight schemes into one line - worth knowing if you extend this)
- Several weeks of backdated workout sessions, weight/step/nutrition entries,
  goals, and check-ins per client, generated through the real
  `PersonalRecordService` so personal records and notifications come from
  the same code path a real logged workout would use
- A welcome message in each coach↔client conversation

Re-running against a non-empty database is a no-op (it checks
`userRepository.count() > 0` and returns immediately) - there's no reset
command; drop and recreate the database (or the Docker volume) to reseed.
