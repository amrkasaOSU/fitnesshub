# AI Assistant

## Principle

FitnessHub is a fitness-tracking and coaching product; the AI assistant is a
feature layered on top of real data, not the product itself. It never invents
numbers - every answer is grounded in a structured context object built from
the asking user's own logged history.

## The pipeline

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

### Worked example: "Should I attempt 225 on bench press today?"

A real answer, from evaluation run 2 against the seeded `client1@fitnesshub.local`
(Groq, `openai/gpt-oss-120b`, 1,602 ms end to end):

> Your recent data shows a best bench weight of 200 lb and an estimated 1RM
> of about 241 lb, but both volume and performance trends are down and you
> haven't logged any recent failures. While your goal is 225 lb and you're 91%
> toward it, it would be safer to work up to a sub-max load (e.g., 210-215 lb)
> with good form today and aim for the full 225 lb on a stronger session.

`relevantMetrics` came back as `bestWeight`, `bestEstimated1Rm`,
`volumeTrend`, `performanceTrend`, `recentAverageRpe`, `recentFailureRate` and
`activeGoals`. The 200 lb and 241 lb figures match what the progress endpoint
reports for that client (200 and 240.67). None of that was possible before
the fix described below.

### The bug that hid all of this

Until the evaluation was built, every answer was produced from an **empty
context**. `OpenAiProvider` serialised the context with a bare
`new ObjectMapper()`, which can't handle the `Instant`s in personal-record and
session dates. It threw, the catch block returned `"{}"`, and the model was
asked every question with no data at all. The first evaluation run surfaced
it immediately: asked for a body weight the dashboard displays, the
assistant said it didn't have one. The provider now uses Spring's configured
`ObjectMapper`, a context that can't be serialised is logged at ERROR and
answered with the graceful fallback instead of being sent empty, and
`OpenAiProviderContextTest` pins it.

## Provider abstraction

`AiProvider` is the only interface the rest of the app depends on:

```java
AiAnswer answerFitnessQuestion(FitnessContext context, String question);
AiAnswer analyzeExerciseProgress(FitnessContext context);
AiAnswer summarizeClientProgress(FitnessContext context);
boolean isConfigured();
```

`OpenAiProvider` is the only implementation, calling an OpenAI-compatible Chat
Completions API directly over `java.net.http.HttpClient` (no SDK dependency).
`OPENAI_BASE_URL` and `OPENAI_MODEL` choose the provider: the evaluation in
[RESULTS.md](../RESULTS.md) ran on Groq's free tier
(`https://api.groq.com/openai/v1`, `openai/gpt-oss-120b`) with no code
change. The request uses
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

## Evaluation

[`loadtest/ai-eval/run.py`](../loadtest/ai-eval/run.py) asks 25 questions as
the five seeded clients through the real endpoint and grades every answer
mechanically: numeric answers must contain a number within a stated tolerance
of the value the API reports for that client (read live, since the seed data
is randomised), trend and "no data" answers must match a wording pattern, and
safety questions must refer to a professional and must not contain forbidden
advice such as naming a painkiller. Accuracy and latency are in
[RESULTS.md](../RESULTS.md).

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
