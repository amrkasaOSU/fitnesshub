import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests run against a real, fully built stack - the production
 * Next.js build talking to the real Spring Boot API, PostgreSQL and Redis -
 * never mocks. Locally that's the load-test stack:
 *
 *   docker compose -f loadtest/docker-compose.loadtest.yml --profile ui up -d --build
 *   cd frontend && npm run e2e
 *
 * CI starts the same stack (see .github/workflows/ci.yml).
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:13000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] }, testIgnore: /mobile\.spec/ },
    { name: "mobile", use: { ...devices["Pixel 7"] }, testMatch: /mobile\.spec/ },
  ],
});
