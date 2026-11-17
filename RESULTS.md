# Results

Every number in this file came from a command that was actually run, and the
command is written next to it. Nothing here is estimated. Raw outputs live
under [`loadtest/results/`](loadtest/results/).

**Read this before quoting a number:** these are measurements of a
single-machine test stack, not of a production deployment and not of real
users. The live site runs on a free tier with different (smaller) hardware.

## Test environment

| | |
|---|---|
| Machine | Apple M4 (10 cores, 16 GB), macOS 26.6.2 |
| Container runtime | Docker 29.5.2 in a Colima VM limited to 6 CPUs / 8 GB |
| Stack | [`loadtest/docker-compose.loadtest.yml`](loadtest/docker-compose.loadtest.yml): backend (Java 21, Spring Boot 3.5), PostgreSQL 16, Redis 7, each in its own container |
| Load generator | k6 2.4.0, on the host, talking to the backend over `localhost` |
| Data | The app's own demo seed (1 coach, 5 clients, 60 workout sessions, 1,044 logged sets), created by `SeedDataRunner` on first boot |
| Connection pool | HikariCP, 10 connections (the app default) |

The load generator shares the machine with the system under test, so absolute
numbers would be higher on dedicated hardware. Before/after comparisons are
valid because both sides ran under identical conditions.

## 1. Baseline

Measured at commit `1d55660` (the state of `main` when this work started),
with only measurement tooling added.

### 1.1 Test coverage (JaCoCo)

```bash
cd backend && ./mvnw -B test
python3 scripts/coverage-summary.py backend/target/site/jacoco/jacoco.csv
```

| Metric | Baseline |
|---|---:|
| Test classes / tests | 3 / 20 |
| Line coverage | 155 / 3,770 = **4.1%** |
| Branch coverage | 46 / 900 = **5.1%** |
| Instruction coverage | 717 / 19,493 = 3.7% |
| Method coverage | 44 / 1,048 = 4.2% |

All 20 tests were pure-calculation unit tests. No test touched a database, an
HTTP endpoint, or the security configuration.

### 1.2 Load test: read mix on seeded data (k6)

```bash
docker compose -f loadtest/docker-compose.loadtest.yml up -d --build
loadtest/run-matrix.sh baseline          # 50, 100, 250, 500 virtual users
loadtest/summarize.py baseline
```

Each virtual user is one signed-in person loading a page, pausing one second,
and loading another: 80% clients (dashboard, today's workout, week strip,
history, weight, exercise library) and 20% the coach (dashboard, roster, a
client's history). Each level ramps for 15 s, holds for 60 s, and the backend
is restarted and warmed between levels. Script:
[`loadtest/k6/read-mix.js`](loadtest/k6/read-mix.js).

**Overall**

| VUs | requests | req/s | p50 ms | p95 ms | p99 ms | max ms | error % |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 50 | 3603 | 41.7 | 5.8 | 41.0 | 54.3 | 137 | 0.00 |
| 100 | 7199 | 83.3 | 4.8 | 40.6 | 55.4 | 263 | 0.00 |
| 250 | 18009 | 208.7 | 4.9 | 39.8 | 57.5 | 400 | 0.00 |
| 500 | 35816 | 414.8 | 6.7 | 46.5 | 124.9 | 625 | 0.00 |

**p95 latency per endpoint (ms)**

| endpoint | 50 VUs | 100 VUs | 250 VUs | 500 VUs |
|---|---:|---:|---:|---:|
| client_dashboard | 15.7 | 12.1 | 13.4 | 25.4 |
| workout_today | 10.2 | 7.8 | 8.9 | 21.2 |
| workout_week | 11.6 | 8.5 | 9.7 | 22.0 |
| workout_history | 16.2 | 14.1 | 15.1 | 31.3 |
| weight_dashboard | 6.9 | 4.7 | 5.6 | 15.7 |
| exercises | 7.0 | 4.4 | 6.0 | 16.2 |
| coach_dashboard | 54.9 | 57.7 | 58.6 | 86.1 |
| coach_clients | 59.7 | 53.8 | 57.6 | 91.1 |
| coach_client_workouts | 19.3 | 17.3 | 16.9 | 40.5 |

**Throughput ceiling** - the same mix with no pause between requests, 100
virtual users for 45 s:

```bash
k6 run -e VUS=100 -e DURATION=45s -e THINK=0 \
  --summary-export loadtest/results/baseline-stress/vus-100.json loadtest/k6/read-mix.js
```

| VUs | requests | req/s | p50 ms | p95 ms | p99 ms | max ms | error % |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 71214 | 1072.2 | 55.2 | 230.9 | 397.3 | 970 | 0.00 |

What the baseline says: on five clients' worth of data the API is not slow.
It holds 500 concurrent users at 0% errors. The two coach endpoints are the
outliers at 4-10x the latency of everything else, and section 1.3 shows why.

### 1.3 SQL statements per request

```bash
loadtest/query-count.sh
```

Counted in PostgreSQL itself with `pg_stat_statements` (reset, send 20
identical requests with nothing else running, divide), so nothing is hidden
by a cache or missed by a logging filter.

| endpoint | path | SQL statements / request | DB time / request (ms) |
|---|---|---:|---:|
| client_dashboard | /api/dashboard | 60.0 | 0.59 |
| workout_today | /api/workouts/today | 33.0 | 0.29 |
| workout_week | /api/workouts/week | 35.0 | 0.19 |
| workout_history | /api/workouts | 82.0 | 0.72 |
| weight_dashboard | /api/weight/dashboard | 3.0 | 0.05 |
| exercises | /api/exercises?pageSize=100 | 1.0 | 0.03 |
| coach_dashboard | /api/coach/dashboard | 415.0 | 2.64 |
| coach_clients | /api/clients | 394.0 | 2.37 |
| coach_client_workouts | /api/clients/{id}/workouts | 83.0 | 0.79 |
| coach_conversations | /api/coach/conversations | 16.0 | 0.06 |

The coach dashboard runs **415 SQL statements** to render one page for a coach
with five clients, and the roster 394. That is the N+1 pattern: the code loads
the client list, then loops over it issuing dozens of queries per client, and
inside that loop issues further queries per workout session and per exercise.
Workout history does the same per row (82 statements for a 20-row page).

### 1.4 Slowest queries (pg_stat_statements + EXPLAIN ANALYZE)

```bash
loadtest/psql.sh -c "select pg_stat_statements_reset()"
k6 run -e VUS=50 -e DURATION=30s loadtest/k6/read-mix.js
loadtest/psql.sh -c "select calls, total_exec_time, mean_exec_time, query
                     from pg_stat_statements order by total_exec_time desc limit 15"
```

Full output: [`baseline-top-statements.txt`](loadtest/results/queries/baseline-top-statements.txt).
In that 50-second window the backend sent PostgreSQL **155,315 statements**
totalling 1,529.9 ms of execution time. The top five by total time:

| Calls | Total ms | Mean ms | Share | Statement |
|---:|---:|---:|---:|---|
| 20,003 | 254.4 | 0.013 | 16.6% | `workout_exercises` by session id |
| 36,268 | 248.2 | 0.007 | 16.2% | `workout_sets` by workout-exercise id |
| 17,295 | 165.3 | 0.010 | 10.8% | `program_exercises` by program-day id |
| 4,118 | 93.7 | 0.023 | 6.1% | `workout_sets` by a list of workout-exercise ids |
| 9,424 | 84.4 | 0.009 | 5.5% | `client_profiles` by user id |

No individual statement is slow: the worst mean is 0.034 ms, and
`EXPLAIN ANALYZE` on the hottest ones
([`baseline-explain-seeded.txt`](loadtest/results/queries/baseline-explain-seeded.txt))
shows execution times of 0.045-0.106 ms. The cost is the *count* - the same
tiny query repeated tens of thousands of times, each one a network round trip
and a connection-pool checkout. So the fix is fewer queries, not faster ones.

### 1.5 Behaviour at scale (synthetic dataset)

Five clients cannot show how the code behaves for a coach with a full roster,
and are too few rows for PostgreSQL to choose anything but a sequential scan.
[`loadtest/scale-seed.sql`](loadtest/scale-seed.sql) adds one coach with **200
clients and a year of history each**: 49,424 workout sessions, 197,696 logged
exercises, 790,784 sets, 73,000 weigh-ins, 73,000 step entries, 80,000
notifications. This data is synthetic and clearly labelled as such; it is
used only for the measurements in this section and section 3.

```bash
loadtest/psql.sh -v clients=200 -v days=365 < loadtest/scale-seed.sql
loadtest/scale-measure.sh > loadtest/results/scale/baseline.md
```

| endpoint (200-client coach / 1-year client) | SQL statements / request | DB time / request (ms) | median response (ms) |
|---|---:|---:|---:|
| coach dashboard | 67801 | 604.0 | 5278 |
| coach client roster | 67195 | 584.2 | 5389 |
| coach conversations | 601 | 10.3 | 58 |
| coach unread badge | 1 | 1.1 | 3 |
| client dashboard | 60 | 1.8 | 11 |
| client today's workout | 31 | 0.7 | 7 |
| client week strip | 35 | 0.3 | 6 |
| client workout history | 80 | 2.1 | 14 |
| client weight dashboard | 3 | 0.4 | 3 |

One load of the coach dashboard for a 200-client coach takes **5.3 seconds and
67,801 SQL statements**. The per-client endpoints are unaffected by roster
size, as expected.

Query plans at this scale, before any index change:
[`scale-explain-before-indexes.txt`](loadtest/results/queries/scale-explain-before-indexes.txt).

### 1.6 Lighthouse (main pages)

```bash
docker compose -f loadtest/docker-compose.loadtest.yml --profile ui up -d --build frontend
node loadtest/lighthouse/run.mjs baseline
```

Lighthouse 12, headless Chrome, default mobile emulation with simulated
throttling, against the **production build** of the frontend (never the dev
server). Each page is audited 3 times and the median kept. Signed-in pages
are audited with a real session cookie, and the script fails if an audit
ends anywhere other than the page it asked for (so a redirect to `/login`
can't be scored by mistake). Per-page summaries:
[`loadtest/results/lighthouse/baseline/summary.json`](loadtest/results/lighthouse/baseline/summary.json).

| page | performance | accessibility | best practices | SEO | LCP ms | TBT ms | CLS | failing a11y audits |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| landing | 99 | 100 | 100 | 100 | 2262 | 18 | 0 | none |
| login | 97 | 100 | 100 | 100 | 2621 | 8 | 0 | none |
| pricing | 98 | 100 | 100 | 100 | 2482 | 11 | 0 | none |
| client-dashboard | 91 | 91 | 93 | 100 | 3423 | 9 | 0 | color-contrast, link-name |
| workout-today | 90 | 91 | 93 | 100 | 3581 | 7 | 0 | color-contrast, link-name |
| progress | 87 | 84 | 96 | 100 | 4030 | 24 | 0 | button-name, color-contrast, link-name |
| coach-dashboard | 90 | 91 | 93 | 100 | 3551 | 56 | 0 | color-contrast, link-name |
| coach-clients | 85 | 91 | 96 | 100 | 4421 | 12 | 0 | color-contrast, link-name |

Public pages are already near-perfect. Every signed-in page loses
accessibility points to the same two audits (`color-contrast`, and
`link-name` - the notification bell is an icon-only link with no accessible
name), and `/progress` also has an icon-only button with no name.

## 2. Integration and end-to-end tests

### 2.1 What was added

| Suite | Tests | What it runs against |
|---|---:|---|
| `AuthFlowIT` | 19 | Registration, login, session cookie, logout, CSRF, role gates, password change, failed-login lockout, session-id rotation |
| `OwnershipIT` | 37 | Cross-tenant access: every client-data endpoint attacked by a signed-out visitor, another client of the same coach, an unrelated coach, and that coach's client |
| `FitnessCalculationsIT` | 20 | Known-answer tests, worked out by hand: Epley 1RM, volume rules, PR eligibility, adherence, weekly weight average, Mifflin-St Jeor calories |
| `WorkoutLifecycleIT` | 5 | Today's workout from "opened the app" to "finished", and what the coach sees at each stage |
| Playwright E2E | 12 | Core flows in a real browser: sign-in/out, coach adds a client then builds and assigns a program, client logs sets and finishes a workout, client logs weight and steps, empty states, phone layout |

The integration tests boot the whole Spring application against real
PostgreSQL 16 and Redis 7 containers (Testcontainers) and talk to it over HTTP
with real session cookies and the CSRF double-submit, exactly as the browser
does. Nothing is mocked. The E2E tests drive the **production build** of the
frontend against the real backend.

Both run in GitHub Actions on every push
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)): `backend` runs
`./mvnw verify` (unit + integration) and publishes the coverage report, and a
new `e2e` job starts the full stack with docker compose and runs Playwright.

### 2.2 Coverage

```bash
cd backend && ./mvnw -B verify      # needs Docker (Testcontainers)
python3 scripts/coverage-summary.py backend/target/site/jacoco/jacoco.csv
```

| Metric | Before | After | Change |
|---|---:|---:|---:|
| Tests | 20 | 101 (20 unit + 81 integration) | +81 |
| Line coverage | 155 / 3,770 = 4.1% | 2,948 / 3,782 = **77.9%** | +73.8 points |
| Branch coverage | 46 / 900 = 5.1% | 505 / 902 = **56.0%** | +50.9 points |
| Instruction coverage | 3.7% | 79.4% | +75.7 points |
| Method coverage | 4.2% | 75.0% | +70.8 points |

```bash
docker compose -f loadtest/docker-compose.loadtest.yml --profile ui up -d --build
cd frontend && npx playwright test --repeat-each=3
```

E2E: **36 / 36 passed** (12 tests x 3 repeats, run to check for flakiness).

### 2.3 Bugs the new tests found

The integration tests were run against the **unchanged** code before any fix,
so each of these was a real, pre-existing bug (74 tests at that point: 70
passed, 4 failed; the extra tests below were then written to pin each bug):

| # | Bug | How it showed up | Fix |
|---|---|---|---|
| 1 | **Session fixation.** Login kept the pre-login session id. An anonymous request opens a session, so an id planted in a victim's browser became a signed-in session. | `signingInIssuesAFreshSessionIdSoAPlantedOneIsWorthless`; reproduced with curl: identical `SESSION` before and after login | Invalidate any pre-login session before establishing the new one |
| 2 | `/api/clients/{id}/progress`, `/nutrition` and `/checkins` returned **200 with the caller's own data** when a client put someone else's id in the path. Not a data leak, but the response claimed to be about a different person. | 3 failing `OwnershipIT` cases | Check access to the path id explicitly, returning 403 |
| 3 | **Opening the app marked today's workout `IN_PROGRESS`**, so the coach dashboard's "training today" counted every client who merely looked at their plan. | `todayResolvesToDayOneOfTheCycle`, `merelyOpeningTheAppIsNotTraining` | An auto-created session starts `NOT_STARTED`; the first logged set promotes it |
| 4 | The client dashboard created today's session inside a **read-only transaction**, so the insert never flushed and the dashboard returned a workout id that did not exist. | `theDashboardsWorkoutIsARealSessionTheClientCanOpen`; reproduced with curl: `GET` on the id returned 404, 0 rows in the DB | Make that method read-write |
| 5 | An unknown timezone was accepted and silently treated as UTC everywhere. | `anUnknownTimezoneIsRejectedInsteadOfSilentlyBecomingUtc` | Validate with `ZoneId.of`, return 400 |
| 6 | **The coach's roster didn't show a newly added client** if the roster was still loading when the client was created: TanStack Query reuses an in-flight first load on invalidation instead of refetching. | Playwright coach flow; the trace showed the roster `GET` (2,358 ms) started before the `POST` and finished after it | Cancel the in-flight query before invalidating |
| 7 | The login and register pages had **no heading** (the title was a styled `div`). | Playwright `getByRole("heading", { name: "Log in" })` | Render the title as an `h1` |
