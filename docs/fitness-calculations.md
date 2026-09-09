# Fitness Calculations

Every calculation below runs on the backend (`docs/architecture.md` explains
why) so the number a client sees always matches what their coach sees. This
document is the single source of truth for what each number means and which
simplifying assumptions it makes - read it before trusting a number, and
update it if you change the underlying formula.

## Estimated 1RM (Epley formula)

```
estimated1RM = weight × (1 + reps / 30)
```

Implemented in `ProgressionService#estimated1Rm`. A single-rep set returns the
weight itself. Only counts sets where `isValidForOneRepMaxAndPr()` is true:
not a warmup, not a failed rep, `reps > 0` and `weight > 0`. Always labeled
"Estimated 1RM" in the UI, never "your 1RM" - it's an estimate from
sub-maximal sets, not a measured max.

## Volume

```
setVolume = weight × reps
sessionVolume = sum(setVolume) over all non-warmup sets
```

Implemented in `ProgressionService#totalVolume`. Unlike the 1RM/PR
calculations, **failed sets still count toward volume** - the client still
moved that weight for those reps; only warmups are excluded. This is a
judgment call, documented here so it's not a silent surprise.

## Personal records

Four types, one row per (client, exercise, record type) in `PersonalRecord`,
updated in place when beaten rather than appended:

| Type | What it tracks | When it's checked |
|---|---|---|
| MAX_WEIGHT | Heaviest weight in a single valid set | On every set completion |
| MAX_REPS | Most reps in a single valid set (any weight) | On every set completion |
| ESTIMATED_1RM | Highest Epley estimate from a single valid set | On every set completion |
| MAX_VOLUME | Highest total volume for that exercise in one session | On workout completion |

"Valid" here means `isValidForOneRepMaxAndPr()` - warmups, failed sets, and
zero-rep entries never count as a PR, matching spec section 28's instruction
not to use failed/warmup sets for 1RM-derived numbers.

## Weekly average body weight

`WeightService#weeklyAverage` computes the arithmetic mean of entries within
a **Monday-Sunday week**, in the client's own timezone. The dashboard
(`WeightService#dashboard`) deliberately shows the **most recently completed**
full week, not the current in-progress one - per spec section 36 ("If
insufficient data exists: 'No complete weekly average yet.'"), a partial week
isn't a meaningful average yet. If the prior completed week has zero entries,
the dashboard returns `null` with that exact message rather than silently
showing zero or the latest single reading.

Contrast this with **step** weekly averages (`StepService#dashboard`), which
deliberately use the **current, in-progress** week - a live step tracker
updating through the week is the expected UX there (matches common step-app
behavior), and steps don't have the same "don't let one bad day look like a
trend" concern that motivated the stricter rule for weight.

## Adherence

```
adherence% = min(completedWorkouts, plannedWorkouts) / plannedWorkouts × 100
```

Implemented in `AdherenceService`. "Planned" means: for each day in the
window, resolve which `ProgramDay` the active program's cycle lands on
(`daysSinceStart % cycleLength`), and count it only if that day has at least
one prescribed exercise - a "Rest" day with no exercises is never planned
work, so it can never be missed. If there's no active program, adherence is
`null` (with `plannedWorkouts = 0`), not `0%` - the client is never penalized
for a coach not having assigned a program yet.

Extra, unscheduled workouts (an ad-hoc session on a rest day, or training
twice in one planned slot) raise `completedWorkouts` but the percentage is
capped at 100% - adherence rewards showing up for what was planned, it
doesn't further reward overtraining relative to the plan.

## Attention flags

`AttentionFlagService` computes a deterministic `Set<AttentionFlag>` per
client - these are coaching signals, never a medical diagnosis:

| Flag | Rule |
|---|---|
| NO_WORKOUT_LOGGED | Zero sessions (any status) in the last 7 days |
| LOW_ADHERENCE | ≥3 planned workouts in the last 7 days and completed/planned < 0.67 |
| WEIGHT_TREND_STALLED | ≥3 weigh-ins in the last 21 days and first-vs-last change ≤ 1.0 lb |
| CALORIE_TRACKING_INCONSISTENT | Fewer than 4 of the last 7 days have a nutrition entry |
| STEP_TARGET_MISSED | 7-day average steps < 70% of the active step goal (with ≥1 entry logged) |
| NO_RECENT_CHECKIN | No check-in submitted in the last 10 days |
| HIGH_RPE_PATTERN | Average RPE ≥ 9 across each of an exercise's last 3 completed sessions |
| RECENT_PERFORMANCE_DROP | An exercise's most recent estimated-1RM is >10% below the better of its two prior sessions |

All thresholds are constants in `AttentionFlagService` - tune them there if a
real coach finds them too sensitive or not sensitive enough; they were chosen
to be reasonable defaults, not calibrated against real usage data.

## Nutrition & calories

- **Calories/protein remaining** = coach-set target minus today's logged
  total. `null` if no target has been set (never assumes a default).
- **Estimated calories burned** (`CalorieEstimationService`) uses the
  **Mifflin-St Jeor** BMR formula:
  ```
  BMR = 10×weight(kg) + 6.25×height(cm) − 5×age + s
  s = +5 (male), −161 (female), −78 (other - midpoint approximation)
  TDEE = BMR × activity multiplier (1.2 sedentary … 1.9 extremely active)
  estimatedActiveCalories = weight(kg) × 0.0005 × today's steps
  estimatedTotalCalories = TDEE + estimatedActiveCalories
  ```
  This is unavoidably an estimate (no wearable/heart-rate data), and the UI
  and API response both label it as such (`CalorieEstimate.note`). It's
  `unavailable` rather than guessed if the client hasn't set height, date of
  birth, sex, or logged a body weight yet.
- **Estimated calorie balance** = calories consumed − estimated total
  calories burned. Always labeled an estimate, never presented as exact,
  per spec section 45.

## Training summary (weekly/period analytics)

`AnalyticsService#trainingSummary` aggregates, for a date range: completed
workouts, planned workouts and adherence % (delegated to `AdherenceService`),
total sets/reps, total volume, average workout duration, average RPE, average
RIR, and PR count in that window (by `PersonalRecord.achievedAt`, which is
also why a coach viewing a client's dashboard sees "PRs (30d)" rather than
"PRs ever" - the record row itself only ever holds the *current* best).

## Known simplifications

- Goal progress percentage (`GoalService#toDto`) is
  `currentValue / targetValue × 100`, clamped to [0, 100]. This reads
  correctly for goals where you're progressing *toward* a higher number
  (strength, steps, adherence%) but doesn't specially handle goals where the
  target is *lower* than the start (e.g. a body-fat-percentage goal) - a
  documented limitation, not a bug someone should "fix" by guessing at
  direction from the goal name.
- `RECENT_PERFORMANCE_DROP` and `HIGH_RPE_PATTERN` look at an exercise's
  literal last 3 *occurrences* across all of a client's sessions (any
  program day), not the last 3 times it appeared in the *same* program slot -
  correct in the common case, but a client cycling many different exercises
  in and out of one slot could see this take longer to trigger.
