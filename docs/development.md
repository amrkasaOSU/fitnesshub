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

**Testcontainers-based integration tests and Playwright E2E tests are
described in the product spec but not included in this pass** - they need a
real Docker daemon, which wasn't available in the sandbox this project was
initially built in (see below). The seams for them exist (repositories,
`AuthorizationService`, and the REST controllers are all plain Spring beans
a `@SpringBootTest` + Testcontainers Postgres could exercise directly) - this
is flagged as a known gap, not silently skipped.

```bash
cd frontend
npm run typecheck   # tsc --noEmit
npm run lint        # eslint
npm run build       # production build (also type-checks)
```

## How this was verified without Docker

The environment this project was first built in had no Docker daemon, no
Homebrew, and no system Postgres/Redis/Node/Maven. Rather than skip
verification, the whole stack was stood up from portable, non-system-installed
binaries:

- **Maven & Node**: official binary tarballs, extracted to a local directory
  and added to `PATH` for the session - no different from a CI runner
  downloading them.
- **PostgreSQL**: the actual `postgres`/`initdb`/`pg_ctl` binaries embedded
  inside the `io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8`
  Maven artifact, extracted directly (without pulling in the whole Java
  library) - this is real PostgreSQL 18, not a mock.
- **Redis**: built from source (`make BUILD_TLS=no`) - Redis's core has no
  external dependencies beyond a C compiler, so `redis-server`/`redis-cli`
  built in under two minutes even with the optional modules (RediSearch,
  RedisJSON) failing to build.

Against that real Postgres + Redis, the backend was booted, all 9 Flyway
migrations applied, the seed data ran, and the full auth → dashboard →
workout-logging → PR-detection → messaging → AI (graceful "not configured")
→ coach-notes pipeline was exercised end-to-end via `curl` and, for the
frontend, the in-app Browser tool (screenshots in the session transcript).
**One real bug was caught this way**: Hibernate's `hibernate.order_inserts`
optimization reordered `workout_sets` inserts ahead of their parent
`workout_exercises` insert within a single flush batch (this schema uses
plain UUID foreign keys, not JPA `@ManyToOne` associations, so Hibernate can't
see the dependency) - fixed by leaving `order_inserts`/`order_updates` at
their default (off); see the comment in `application.yml`. A second bug
(`/actuator/health` 404-turned-500 because `spring-boot-starter-actuator` was
referenced in config but never added as a dependency) was also caught this
way.

None of this local-verification tooling is part of the shipped application -
`docker-compose.yml` is the real, supported way to run this project, and CI
(`.github/workflows/ci.yml`) runs against real `postgres:16-alpine` and
`redis:7-alpine` service containers.

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
