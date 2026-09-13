# Deployment

Two zero-cost walkthroughs exist: [deployment-oracle.md](deployment-oracle.md)
(one always-on VM, no cold starts) and
[deployment-render.md](deployment-render.md) (managed free tiers, but the
backend sleeps). This document covers the rules that apply wherever you host.

## The one thing you must not forget

Set **`SPRING_PROFILES_ACTIVE=prod`** on the backend.

The base `application.yml` is tuned for local development, where seeding demo
data and an open Swagger UI are conveniences. Inheriting those on a live
instance would create accounts whose password is published in this repository's
README - effectively handing anyone a coach login to real client data.

`application-prod.yml` turns off seeding and springdoc and forces
`cookie.secure=true`. As a second line of defence `SeedDataRunner` is annotated
`@Profile("!prod")`, so demo data cannot be created under that profile even if
`SEED_ENABLED=true` is set by mistake. This is tested: booting with the prod
profile, `SEED_ENABLED=true`, and a completely empty database produces a
database with no users, and the demo coach login returns 401.

## Required environment variables

| Variable | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod`. See above. |
| `DATABASE_URL` | `jdbc:postgresql://host:5432/dbname` |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | From your database provider |
| `REDIS_URL` | `redis://...`; sessions and rate limiting both use it |
| `FRONTEND_URL` | Exact origin of the frontend, e.g. `https://app.example.com`. This is the CORS allowlist - a mismatch makes every API call fail. |
| `NEXT_PUBLIC_API_URL` | On the *frontend*: the backend's origin, e.g. `https://api.example.com` |

Optional: `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`, `STRIPE_SECRET_KEY`,
`STRIPE_WEBHOOK_SECRET`. Leave them unset and those features report themselves
as unconfigured via `GET /api/features`; everything else works.

## The frontend and the API must share an origin

Session cookies default to `SameSite=Lax`, which browsers do not send on
cross-**site** requests. If the frontend and backend sit on unrelated domains -
a Vercel app calling a Render service, say - then *every* authenticated request
fails, login included. The usual workaround is `SameSite=None`, which is
strictly weaker. Don't do that; pick one of these instead.

### Option A - one host, reverse proxy in front

Both services on one machine behind Caddy, which routes `/api/*` to the backend
and everything else to Next.js. This is what `docker-compose.prod.yml` does; see
[deployment-oracle.md](deployment-oracle.md).

    browser ──> Caddy ─┬─ /api/*  ──> backend
                       └─ /*       ──> frontend

### Option B - split hosts, Next proxies the API

When the backend has to live somewhere else - a free tier that only runs one
service, a platform without capacity - set **`BACKEND_ORIGIN`** on the frontend.
Next then forwards `/api/*` to the backend *server-side*, so the browser still
only ever talks to the frontend's own origin.

    browser ──> Next (Vercel) ─┬─ /api/*  ──server-side──> backend (anywhere)
                               └─ /*       ──> the app itself

Set both of these on the frontend:

| Variable | Value |
|---|---|
| `BACKEND_ORIGIN` | `https://your-backend.example.com` (server-side only, not public) |
| `NEXT_PUBLIC_API_URL` | *empty* - makes the browser use relative URLs |

The backend still needs `FRONTEND_URL` set to the frontend's origin. CORS won't
actually be exercised, since the browser never makes a cross-origin call, but
the value is also used elsewhere.

The tradeoff is one extra hop: API calls go browser -> frontend host -> backend.
For a handful of clients that is not noticeable, and it buys the freedom to host
the backend anywhere at all.

## First deploy checklist

1. Provision Postgres and Redis; note the connection strings.
2. Deploy the backend with the variables above. Flyway migrates on boot.
3. **Register your own coach account** through the UI. There are no seeded
   accounts in production, by design - you create the first user yourself.
   The 24-exercise library ships as migration V12, so it's there before you
   log in; only demo *users* are absent.
4. Deploy the frontend with `NEXT_PUBLIC_API_URL` pointing at the backend.
5. Build a program (Programs -> New program), add your client, and assign it.
   A client with no program assigned sees "No program yet" and cannot log
   workouts, so assignment is part of onboarding, not an afterthought.
6. Sign in as the client with the temporary password and log one set yourself
   before handing the account over.

## Operational notes

- **Backups.** Client body weight and check-in notes are personal data. Use a
  managed Postgres with automated backups; don't run your own.
- **Login throttling** allows 10 failed attempts per email and per IP in a
  15-minute window (`fitnesshub.auth.login-rate-limit.*`). Counters live in
  Redis, so they hold across instances and reset on a successful login.
- **Password recovery** runs through the coach - there is no email delivery.
  A locked-out client is reset from their detail page. Worth telling clients
  up front so they know who to contact.
