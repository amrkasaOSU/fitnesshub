# Security

## Authentication

- Passwords are hashed with **BCrypt** (strength 12), never stored or logged
  in plaintext.
- Login/registration issue a server-side session, persisted to **Redis** via
  Spring Session, identified by an `HttpOnly` cookie. There is no JWT and no
  token is ever stored in `localStorage` or `sessionStorage`.
- CSRF protection uses the double-submit-cookie pattern
  (`CookieCsrfTokenRepository`, readable by JS as `XSRF-TOKEN` so the frontend
  can echo it back as `X-XSRF-TOKEN`). `/api/auth/login`, `/api/auth/register`,
  and the Stripe webhook are the only endpoints exempted (the first two have no
  session yet to protect; the webhook is verified by Stripe's own signature
  instead).
- CORS is locked to `fitnesshub.cors.allowed-origins` (the frontend's own
  origin) with `allowCredentials(true)`.

## Authorization

**`CurrentUser`** (`com.fitnesshub.security.CurrentUser`) is the only place
identity is read out of the request - it pulls the authenticated principal
from `SecurityContextHolder`, never from a path/query/body parameter. Every
service that needs "who is calling" injects this instead of accepting a
caller-supplied ID.

**`AuthorizationService`** is the only place ownership is checked:

- `resolveClientId(requestedClientId)` - a CLIENT always resolves to
  themselves (a `clientId` query param is ignored for them); a COACH must
  supply one and must have an **ACTIVE** `CoachClient` row for it; an ADMIN
  can pass any ID.
- `assertCoachOwnsClient(coachId, clientId)` / `assertCanAccessClient(clientId)`
  - used anywhere a coach, client, or admin might reach the same endpoint with
  different ownership rules (e.g. `GET /api/clients/{id}`).

Role-based URL rules in `SecurityConfig` (`/api/coach/**` requires
COACH/ADMIN, `/api/admin/**` requires ADMIN) are a coarse first filter;
the real ownership check always happens in the service layer via
`AuthorizationService`, because a role alone doesn't prove a coach owns
*this particular* client.

Coach private notes (`CoachNote`) are fetched only through
`CoachClientService`, which requires the caller to be the coach who owns the
note's client - there is no code path that returns a coach note to a client
account.

## Input validation

Bean Validation (`jakarta.validation`) annotations on every request DTO -
`@Positive`, `@DecimalMin`/`@DecimalMax` for RPE/RIR (0-10), `@Min`/`@Max` for
percentages and check-in scores (1-5), etc. Violations return a `400` with a
structured `VALIDATION_ERROR` body (field + message), never a raw stack trace.
Database-level `CHECK` constraints back up the same rules as a second layer
(see the migration files) in case a row is ever written outside the API.

## Error handling

`GlobalExceptionHandler` maps every exception type to a stable
`{ "error": { "code", "message", "details" } }` shape and an appropriate HTTP
status. The catch-all handler logs the real exception server-side but returns
a generic "An unexpected error occurred" message - no stack traces or
internal details ever reach the client.

## AI privacy

The AI pipeline (see [docs/ai.md](ai.md)) resolves the acting client through
the same `AuthorizationService.resolveClientId` as every other client-data
endpoint, so a client's question can only ever pull that client's own data,
and a coach's client-analysis request goes through
`assertCoachOwnsClient` first. The raw question and structured answer are
persisted (`AIInsightRequest`/`AIInsightResponse`) for audit purposes, scoped
to the same ownership rules as everything else - no separate access path
exists to read another user's AI history.

## Stripe webhook verification

`BillingService#handleWebhook` verifies the `Stripe-Signature` header via
`Webhook.constructEvent(...)` before touching the payload - an unsigned or
mis-signed request is rejected with a 409 before any subscription state
changes. Subscription tier/status is **only** ever written from a verified
webhook event or the checkout-session lookup it triggers - the frontend
cannot set its own plan.

## Not implemented yet

- Email verification and password-reset flows have the necessary schema
  fields (`User.emailVerified`) but no email-sending integration.
- Rate limiting is implemented for AI requests (Redis-backed, see
  [docs/ai.md](ai.md)); general login-attempt rate limiting is not yet wired
  up. Flagging this explicitly rather than leaving it undocumented.
