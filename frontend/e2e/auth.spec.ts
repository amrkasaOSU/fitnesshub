import { expect, test } from "@playwright/test";
import { COACH, PASSWORD, signIn, submitLogin } from "./helpers";

test("a signed-out visitor is sent to the login page", async ({ page }) => {
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();
});

test("a wrong password shows an error and stays on the login page", async ({ page }) => {
  await submitLogin(page, COACH, "not-the-password");
  await expect(page.getByText("Invalid email or password.")).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});

test("the login form validates before calling the server", async ({ page }) => {
  let loginCalls = 0;
  page.on("request", (r) => {
    if (r.url().endsWith("/api/auth/login")) loginCalls++;
  });
  await page.goto("/login");
  const email = page.getByLabel("Email");
  await email.fill("not-an-email");
  await page.getByRole("button", { name: "Log in" }).click();

  // type="email" lets the browser block the submit itself.
  expect(await email.evaluate((el: HTMLInputElement) => el.validity.valid)).toBe(false);
  await expect(page).toHaveURL(/\/login$/);
  expect(loginCalls).toBe(0);
});

test("a coach lands on the coach dashboard and can log out", async ({ page }) => {
  await signIn(page, COACH);
  await expect(page).toHaveURL(/\/coach\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Coach Dashboard" })).toBeVisible();

  await page.getByRole("button", { name: "Log out" }).first().click();
  await expect(page).toHaveURL(/\/login$/);
  // The session is really gone, not just the UI state.
  await page.goto("/coach/dashboard");
  await expect(page).toHaveURL(/\/login$/);
});

test("a client lands on their own dashboard", async ({ page }) => {
  await signIn(page, "client1@fitnesshub.local", PASSWORD);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByText("Weekly Adherence")).toBeVisible();
});

test("a client cannot open coach pages", async ({ page }) => {
  await signIn(page, "client2@fitnesshub.local");
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.goto("/coach/clients");
  // The API refuses the data; the page must not show another client's roster.
  await expect(page.getByText("client1@fitnesshub.local")).toHaveCount(0);
});
