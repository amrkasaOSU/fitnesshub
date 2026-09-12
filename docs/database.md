# Database

PostgreSQL, managed entirely through Flyway migrations
(`backend/src/main/resources/db/migration/V1__...` through `V9__...`).
Hibernate's `ddl-auto` is set to `validate` - it checks the schema matches the
entities but never generates DDL itself.

## Design conventions used throughout

- **UUID primary keys** everywhere, generated in-app (Hibernate's default UUID
  generator), not database `gen_random_uuid()` defaults - the column default
  exists as a safety net for direct SQL inserts, not the normal path.
- **Plain UUID foreign-key columns, not JPA `@ManyToOne` associations.** Every
  relationship (e.g. `WorkoutSet.workoutExerciseId`) is a bare column, not an
  object reference. This keeps entities simple and avoids N+1 lazy-loading
  surprises, at the cost of needing explicit repository lookups to "join" data
  in services. One real consequence of this choice: Hibernate's
  `hibernate.order_inserts` optimization batches inserts by entity type and
  can't see these FK dependencies, so it can reorder a child insert ahead of
  its parent's and break a real constraint. That setting is deliberately left
  off (see the comment in `application.yml`) - discovered by seeding a full
  demo dataset against real PostgreSQL during development.
- **BigDecimal for every measurement that matters**: body weight, workout
  weight, volume, estimated 1RM, calories, macros. Never `double`/`float`.
- **Every table has `created_at`/`updated_at`** via `BaseEntity` +
  `@EnableJpaAuditing`; several also have a separate business-meaning
  timestamp (e.g. `WorkoutSession.started_at` vs. its inherited `created_at`).

## Entity map

| Entity | Package | Notes |
|---|---|---|
| User | user | Single table for all three roles (ADMIN/COACH/CLIENT) |
| CoachProfile | coach | 1:1 with a COACH user |
| ClientProfile | client | 1:1 with a CLIENT user; holds `coachId`, goals, targets, unit system |
| CoachClient | coach | The coach↔client relationship, with status (ACTIVE/PAUSED/ENDED) |
| CoachNote | coach | Private to the coach; never exposed on any client-facing endpoint |
| Exercise | exercise | Shared library; `isSystemExercise` distinguishes seeded vs. coach-added |
| Program / ProgramDay / ProgramExercise | program | A program's prescribed structure |
| ClientProgram | program | One client's assignment of a program, with start/end date and status |
| WorkoutSession / WorkoutExercise / WorkoutSet | workout | What a client actually did |
| WorkoutFeedback | workout | Coach's comment on a specific completed session |
| PersonalRecord | progress | One row per (client, exercise, record type) - updated in place, not appended |
| WeightEntry | bodyweight | |
| StepEntry | steps | Unique per (client, date) |
| NutritionEntry | nutrition | Unique per (client, date) - see docs/fitness-calculations.md for why it's a daily total, not per-meal |
| DailyActivity | nutrition | Calorie estimates are computed on read rather than persisted here, so a change to the formula doesn't leave stale rows behind |
| Goal | goal | **Named `Goal`, not `FitnessGoal`** - the obvious name collides with `client.FitnessGoal`, the enum of goal categories (FAT_LOSS, STRENGTH, ...). Renamed to avoid two same-named types in the codebase. |
| CheckIn | goal | Weekly check-in; unique per (client, week start date) |
| ProgressPhoto | photo | Transformation photos. Bytes live in a `bytea` column - see the comment in `V11__progress_photos.sql` for why, and what to change if it ever outgrows that. Listing projects metadata only so a gallery never loads image data |
| Conversation / Message | messaging | One conversation per (coach, client) pair |
| Notification / NotificationPreference | notification | |
| Subscription | subscription | One row per user; `type` is COACH_PLAN or CLIENT_PLAN, `tier` is FREE/PRO/PREMIUM (PRO only valid with COACH_PLAN, PREMIUM only with CLIENT_PLAN - enforced in code, not the schema) |
| AIInsightRequest / AIInsightResponse | ai | Stores the question and the structured answer for audit/history |
| AuditLog | audit | Append-only action log |

## Indexes

Every foreign-key-shaped column used in a `WHERE` clause has an index -
`client_id` on every per-client table, `started_at`/`date` on time-series
tables, plus the specific ones the spec calls out (`User.email`,
`CoachClient.coachId`/`clientId`, `WorkoutSession.clientId`/`startedAt`,
`WorkoutSet.workoutExerciseId`, `WeightEntry.clientId`/`recordedAt`,
`StepEntry.clientId`/`date`, `NutritionEntry.clientId`/`date`,
`Message.conversationId`, `Notification.userId`). See the migration files for
the exact list.

## Program versioning (immutable history)

A `Program` a client has been assigned to is never mutated in place once it
has at least one `ClientProgram` row pointing at it. `ProgramService#update`
checks for that and, if found, creates a **new** `Program` row
(`version = previous + 1`, `previousVersionId` set) with fresh
`ProgramDay`/`ProgramExercise` rows, leaving the original untouched. Existing
`WorkoutSession` rows reference `programDayId` values that belong to whichever
version was live when the client trained, so historical workouts never
silently change shape underneath a coach's edit.
