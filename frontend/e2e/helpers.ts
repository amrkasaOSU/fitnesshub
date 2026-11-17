import { expect, request, type APIRequestContext, type Page } from "@playwright/test";

/** The backend the frontend was built against; setup helpers call it directly. */
export const API = process.env.E2E_API_URL ?? "http://localhost:18080";
/** Published demo password; only exists in seeded, non-production databases. */
export const PASSWORD = "FitnessHub!2024";
export const COACH = "coach@fitnesshub.local";

export const uniqueEmail = (prefix: string) =>
  `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}@e2e.fitnesshub.local`;

/** Fills and submits the login form without waiting for the outcome. */
export async function submitLogin(page: Page, email: string, password = PASSWORD) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Log in" }).click();
}

/** Signs in and waits until the app has redirected away from /login. */
export async function signIn(page: Page, email: string, password = PASSWORD) {
  await submitLogin(page, email, password);
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

/** A signed-in API client for arranging test data faster than clicking through it. */
export async function apiAs(email: string, password = PASSWORD): Promise<APIRequestContext> {
  const ctx = await request.newContext({ baseURL: API });
  const login = await ctx.post("/api/auth/login", { data: { email, password } });
  expect(login.status(), `API login as ${email}`).toBe(200);
  await ctx.get("/api/auth/csrf");
  return ctx;
}

async function csrfHeader(ctx: APIRequestContext) {
  const state = await ctx.storageState();
  const token = state.cookies.find((c) => c.name === "XSRF-TOKEN")?.value ?? "";
  return { "X-XSRF-TOKEN": token };
}

export async function apiPost<T = unknown>(ctx: APIRequestContext, path: string, data: unknown): Promise<T> {
  const res = await ctx.post(path, { data, headers: await csrfHeader(ctx) });
  expect(res.ok(), `POST ${path}: ${res.status()} ${await res.text()}`).toBeTruthy();
  const text = await res.text();
  return (text ? JSON.parse(text).data : undefined) as T;
}

export async function exerciseId(ctx: APIRequestContext, name: string): Promise<string> {
  const res = await ctx.get("/api/exercises?pageSize=100");
  const page = (await res.json()).data as { data: { id: string; name: string }[] };
  const match = page.data.find((e) => e.name === name);
  if (!match) throw new Error(`exercise library has no ${name}`);
  return match.id;
}

/**
 * A brand-new client of the demo coach, on a one-day program that repeats
 * every day, so "today" always has exercises whatever the date.
 */
export async function newClientWithDailyProgram(): Promise<{ email: string; firstName: string }> {
  const coach = await apiAs(COACH);
  const email = uniqueEmail("e2e-client");
  const firstName = "E2e" + Math.random().toString(36).slice(2, 6);
  const client = await apiPost<{ id: string }>(coach, "/api/coach/clients", {
    email, temporaryPassword: PASSWORD, firstName, lastName: "Tester", fitnessGoal: "STRENGTH",
  });
  const bench = await exerciseId(coach, "Bench Press");
  const squat = await exerciseId(coach, "Squat");
  const program = await apiPost<{ id: string }>(coach, "/api/programs", {
    name: `E2E daily ${firstName}`, durationWeeks: 4, goal: "STRENGTH",
    days: [{
      dayNumber: 1, name: "Full Body",
      exercises: [
        { exerciseId: bench, orderIndex: 0, sets: 3, targetReps: 5, restSeconds: 90 },
        { exerciseId: squat, orderIndex: 1, sets: 3, targetReps: 5 },
      ],
    }],
  });
  const today = new Date().toISOString().slice(0, 10);
  await apiPost(coach, `/api/programs/${program.id}/assign`, { clientId: client.id, startDate: today });
  await coach.dispose();
  return { email, firstName };
}
