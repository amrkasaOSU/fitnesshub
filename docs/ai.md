# AI Assistant

## Principle

FitnessHub is a fitness-tracking and coaching product; the AI assistant is a
feature layered on top of real data, not the product itself. It never invents
numbers - every answer is grounded in a structured context object built from
the asking user's own logged history.

## The pipeline (spec section 73)

```
User question
  → Authentication            (Spring Security - already done before the controller runs)
  → Authorization             (AuthorizationService.resolveClientId / assertCoachOwnsClient)
  → Rate limit check          (AiRateLimiter, Redis-backed)
  → Data retrieval            (FitnessContextService)
  → Context construction      (FitnessContext record)
  → AI provider call          (AiProvider.answerFitnessQuestion / summarizeClientProgress)
  → Structured response parse (OpenAiProvider parses the model's JSON)
  → Safety pass               (AiService.applySafetyPass)
  → Response
```

All of this lives in `com.fitnesshub.ai.AiService` and its collaborators -
there's no path where a raw question is forwarded to the model without the
context/safety steps around it.

## Data retrieval: `FitnessContextService`

Given a client ID and the raw question text, it:

1. Looks for an exercise name mentioned in the question (case-insensitive
   substring match against the exercise library) and, if found, pulls that
   exercise's full progress summary (`ExerciseProgressService` - the same
   1RM/volume/trend engine that powers `/progress`).
2. Always includes: weekly adherence, recent personal records, weight
   dashboard, nutrition dashboard, step dashboard, active goals, and a
   30-day completed-workout count.

This is intentionally not a fancy NLP classifier - the exercise library is
small enough that substring matching works, and the broader context (weight,
adherence, nutrition) is cheap enough to always include rather than trying to
predict what's relevant.

### Worked example: "Should I attempt 225 on bench today?"

For the seeded `client1@fitnesshub.local`, the context includes bench press's
last several sessions - weight climbing 185→190→195→200 lb, RPE climbing
alongside it, with the most recent estimated 1RM essentially flat versus the
session before it. The model is handed that data (not told the answer) and
is expected to reason "your estimated 1RM has plateaued around 240 lb, RPE has
been high the last two sessions - 225 would be an aggressive jump" rather than
a scripted response. See `SeedDataRunner#seedClient1_StrengthSuccess` for how
that history is built, and `ProgressionServiceTest` for the underlying math.

## Provider abstraction

`AiProvider` is the only interface the rest of the app depends on:

```java
AiAnswer answerFitnessQuestion(FitnessContext context, String question);
AiAnswer analyzeExerciseProgress(FitnessContext context);
AiAnswer summarizeClientProgress(FitnessContext context);
boolean isConfigured();
```

`OpenAiProvider` is the only implementation, calling OpenAI's Chat Completions
API directly over `java.net.http.HttpClient` (no SDK dependency) with
`response_format: json_object` and a system prompt that both defines the
output schema and states the safety rules below. Swapping providers means
writing a new `AiProvider` implementation, not touching `AiService`.

### Graceful degradation

If `OPENAI_API_KEY` is blank, `OpenAiProvider.isConfigured()` returns `false`
and every call returns `AiAnswer.notConfigured()` - a clear, non-error message
- instead of throwing. A network failure or non-200 response from OpenAI
returns `AiAnswer.providerError(...)` the same way. The AI feature can never
500 the request it's attached to.

## Safety

The system prompt instructs the model to never diagnose injuries or diseases,
never prescribe medication, never claim to replace a medical professional,
never guarantee results, and never give dangerous training instructions - and
to recommend consulting a qualified professional when a question touches
those areas. On top of that, `AiService.applySafetyPass` does a keyword check
on the raw question (pain, injury, diagnose, medication, doctor, ...) and
appends a standard disclaimer to the response's `warnings` if the model didn't
already include one - belt-and-suspenders, not a replacement for the prompt.

The AI **never** modifies a program, goal, or calorie target itself. It can
suggest a weight range in `recommendation`, but changing anything still
requires an explicit action by the coach or client through the normal
endpoints.

## Rate limiting

`AiRateLimiter` uses Redis `INCR`/`EXPIRE` on a per-user, per-calendar-day key
(`ai-rate:{userId}:{date}`), so it's correct even with multiple backend
instances. Limits (`fitnesshub.ai.rate-limits.*`, defaults 5/50/100 per day
for free clients / premium clients / coaches) are read from
`SubscriptionService.isPaid(userId)` to pick the right tier.

## Storage

Every request is persisted as `AIInsightRequest` (question, asking user,
target client) and `AIInsightResponse` (the serialized `AiAnswer`, model
name), scoped to the same ownership rules as every other client-data
endpoint - there is no separate access path to another user's AI history.
