# Deploying free on Vercel + Render + Neon + Upstash

Four free services, no credit card on any of them, live in about half an hour.
Use this when you can't get an always-on VM (Oracle's free ARM capacity is
frequently exhausted - see [deployment-oracle.md](deployment-oracle.md)).

| Piece | Service | Notes |
|---|---|---|
| Frontend | Vercel | Free, never sleeps |
| Backend | Render web service | Free, **sleeps after 15 min idle** |
| Postgres | Neon | Free, and does not expire |
| Redis | Upstash | Free, 10k commands/day |

**Read this before you start:** Render's free tier spins down after 15 minutes
of inactivity and takes roughly 50 seconds to wake. Your client hits that once
when they open the app, then it stays warm for their session. It is the price of
free always-available compute, and the only way to remove it is an always-on
host. Tell your client so a slow first load doesn't look broken.

Everything is wired so the browser only ever talks to the Vercel domain - Next
forwards `/api/*` to Render server-side. That keeps session cookies same-origin;
see [deployment.md](deployment.md) for why that matters.

## 1. Postgres on Neon

1. neon.tech -> sign up -> **Create project**. Any region near you.
2. Copy the connection string. It looks like:

       postgresql://alex:npg_AbC123@ep-cool-name-12345.us-east-2.aws.neon.tech/neondb?sslmode=require

3. **Split it into three values** - this app takes the JDBC URL and the
   credentials separately, and JDBC will not accept the `postgresql://user:pass@`
   form:

   | Variable | From the example above |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://ep-cool-name-12345.us-east-2.aws.neon.tech/neondb?sslmode=require` |
   | `POSTGRES_USER` | `alex` |
   | `POSTGRES_PASSWORD` | `npg_AbC123` |

   Note the `jdbc:` prefix, the username and password removed from the host
   part, and `?sslmode=require` kept - Neon rejects unencrypted connections.

## 2. Redis on Upstash

1. upstash.com -> sign up -> **Create database** -> pick a region.
2. On the database page find the **Redis URL** (not the REST URL). It starts
   with `rediss://` - two s's, meaning TLS:

       rediss://default:AbCdEf123@us1-cool-name-12345.upstash.io:6379

3. That whole string is `REDIS_URL`. Spring understands `rediss://` natively.

## 3. Backend on Render

1. render.com -> sign up with GitHub -> **New +** -> **Web Service**
2. Connect the `fitnesshub` repo.
3. Settings:
   - **Root Directory:** `backend`
   - **Runtime / Language:** **Docker** (it will find `backend/Dockerfile`)
   - **Instance Type:** **Free**
   - **Health Check Path:** `/actuator/health`
4. Add environment variables:

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DATABASE_URL` | from step 1 |
   | `POSTGRES_USER` | from step 1 |
   | `POSTGRES_PASSWORD` | from step 1 |
   | `REDIS_URL` | from step 2 |
   | `FRONTEND_URL` | your Vercel URL - fill in after step 4, then redeploy |
   | `PORT` | `8080` |

5. **Create Web Service.** The first build takes 5-10 minutes.

When it's up, note the URL (`https://fitnesshub-backend-xxxx.onrender.com`) and
check it:

```bash
curl https://your-backend.onrender.com/actuator/health   # {"status":"UP"}
```

If that 502s, open the Render logs. The usual causes are a malformed
`DATABASE_URL` (missing the `jdbc:` prefix) or the free instance running out of
memory during startup.

## 4. Frontend on Vercel

1. vercel.com -> sign up with GitHub -> **Add New** -> **Project** -> import
   `fitnesshub`.
2. **Root Directory:** `frontend`. Framework auto-detects as Next.js.
3. Environment variables - **both matter**:

   | Key | Value |
   |---|---|
   | `BACKEND_ORIGIN` | `https://your-backend.onrender.com` (no trailing slash) |
   | `NEXT_PUBLIC_API_URL` | *leave the value completely empty* |

   `BACKEND_ORIGIN` makes Next proxy `/api/*` to Render server-side. The empty
   `NEXT_PUBLIC_API_URL` makes the browser use relative URLs so it never calls
   Render directly. Both are needed; either one alone breaks authentication.

4. **Deploy.**

## 5. Close the loop

Go back to Render and set `FRONTEND_URL` to your Vercel URL
(`https://fitnesshub-xxxx.vercel.app`), then redeploy the backend.

## 6. Verify before your client sees it

Everything through the Vercel domain:

```bash
curl -s https://your-app.vercel.app/actuator/health    # proxied to Render
```

Then in a browser:

1. **Register your own coach account** - production has no seeded accounts.
2. **Programs -> New program** - build a training cycle.
3. Add your client, open the program, and **assign** it to them. A client with
   no program cannot log workouts.
4. Sign in as the client with the temporary password, change it, and log one set
   before handing the account over.

Open the browser devtools Network tab while you do this. Every request should go
to your Vercel domain and none to `onrender.com`. If you see calls to Render
directly, `NEXT_PUBLIC_API_URL` isn't empty.

## Operating it

**Backups.** Neon keeps point-in-time history on the free tier, but take your
own dumps too - the database holds client body weight and check-in notes:

```bash
pg_dump "postgresql://user:pass@host/neondb?sslmode=require" | gzip > backup-$(date +%F).sql.gz
```

**Updates** deploy automatically: both services watch `main`, so `git push`
redeploys. Flyway applies new migrations on backend boot.

**Moving off the free tier later** means changing one variable. Put the backend
on a host that doesn't sleep, update `BACKEND_ORIGIN` on Vercel, redeploy. No
code changes.
