# Architecture

## High-level shape

```
Next.js frontend  →  Spring Boot REST API  →  Domain/service layer  →  Repository layer  →  PostgreSQL
                                            ↘  Redis (sessions, AI rate limiting, caching)
```

This is a **modular monolith**, not microservices: one Spring Boot process, one
database, domains separated by Java package rather than by network boundary.
That keeps transactions simple (a workout completion + PR detection + a
notification all happen in one `@Transactional` unit) while still giving each
domain a clean seam if it ever needs to be pulled out later.

The frontend never talks to PostgreSQL directly - every read and write goes
through the REST API, which is the only place authorization is enforced.

## Backend package layout

Each domain under `com.fitnesshub.*` owns its own entities, repositories,
services, controllers, and DTOs:

```
auth          registration, login, session establishment
user          the User entity shared by all roles
coach         CoachProfile, CoachClient relationship, CoachNote, coach-side client management
client        ClientProfile, client-facing profile/detail endpoints
exercise      the exercise library
program       Program / ProgramDay / ProgramExercise / ClientProgram (+ versioning)
workout       WorkoutSession / WorkoutExercise / WorkoutSet / WorkoutFeedback, the logging flow
progress      ProgressionService (1RM/volume/trends), PersonalRecordService
bodyweight    WeightEntry + weekly-average logic
steps         StepEntry
nutrition     NutritionEntry, DailyActivity, calorie estimation
goal          Goal ("FitnessGoal" in the spec - renamed to avoid a name collision, see docs/database.md), CheckIn
messaging     Conversation / Message
notification  Notification, NotificationPreference, scheduled reminder jobs
analytics     AdherenceService, AttentionFlagService, AnalyticsService, DashboardService
ai            AiProvider abstraction, OpenAiProvider, FitnessContextService, AiService, rate limiting
subscription  Subscription entity, SubscriptionService, Stripe BillingService
report        PDF progress reports (PDFBox), CSV export
audit         AuditLog + AuditService
security      CurrentUser, AuthorizationService (ownership checks), Spring Security config
common        BaseEntity, ApiResponse/PageResponse envelopes, GlobalExceptionHandler
config        SecurityConfig, OpenApiConfig, JPA config, seed data runner
```

Within a domain, the usual shape is `Entity → Repository → Service → Controller`,
with `dto/` records for request/response shapes and a `Mapper` where the
translation is non-trivial. There is no single "God service" - `WorkoutService`
handles workout logging, `ProgressionService` handles the math, `AdherenceService`
handles adherence, `AttentionFlagService` composes signals from several domains.

## Cross-domain dependencies

A few services intentionally depend on other domains' repositories directly
(e.g. `AttentionFlagService` reads workout, weight, nutrition, and check-in
data). That's a deliberate tradeoff for a modular monolith: it keeps the
aggregation logic in one readable place instead of scattering partial
computations across five controllers. If a domain ever needs to become a
separate service, the repository calls at that boundary are the seam to cut.

## Authentication & session model

Session-cookie based, not JWT (see [docs/security.md](security.md) for the
full authorization model): Spring Security authenticates against
`FitnessHubUserDetailsService`, and the resulting `SecurityContext` is
persisted to Redis via Spring Session. `CurrentUser` is the single place that
reads "who is calling" out of that context - no service ever trusts a
client-supplied user/client ID without going through `AuthorizationService`.

## Frontend structure

```
src/app/                    Next.js App Router pages
  (app)/                    route group for every authenticated page - the
                             layout here does the auth check and renders AppShell
  login, register, pricing  public pages
src/components/             shared UI (AppShell, StatCard, RestTimer, shadcn/ui primitives)
src/lib/                    api-client (fetch + CSRF handling), types (mirrors backend DTOs),
                             use-auth, use-pending-set-queue (offline-resilient set logging)
```

Server state lives in TanStack Query; local component state is reserved for
forms, the rest timer, and other purely-client concerns. Every fitness
calculation (1RM, volume, adherence, PR detection) happens on the backend -
the frontend only renders numbers it's given. A client and their coach must
never see different values for the same metric, which is only guaranteed if
there is exactly one implementation of each formula.

## Infrastructure

`docker-compose.yml` runs four services: `postgres`, `redis`, `backend`,
`frontend`. Flyway migrations run automatically on backend startup; a seed
data runner (`SeedDataRunner`, gated by `fitnesshub.seed.enabled`) populates a
demo coach + 5 clients the first time it finds an empty `users` table.
