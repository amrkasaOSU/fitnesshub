import { expect, test } from "@playwright/test";
import { newClientWithDailyProgram, signIn } from "./helpers";

test("a client logs sets and finishes today's workout", async ({ page }) => {
  const { email } = await newClientWithDailyProgram();
  await signIn(page, email);
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.getByRole("link", { name: /go to today's workout/i }).click();
  await expect(page).toHaveURL(/\/workout\/today$/);
  await expect(page.getByRole("heading", { name: "Full Body" })).toBeVisible();
  await expect(page.getByText("0 / 2 exercises")).toBeVisible();

  const bench = page.locator("[data-slot=card]", { hasText: "Bench Press" });
  await bench.getByPlaceholder("Weight").fill("135");
  await bench.getByPlaceholder("Reps").fill("5");
  await bench.getByPlaceholder("RPE").fill("8");
  await bench.getByRole("button", { name: "Complete Set" }).click();

  // The first-ever set on an exercise is a personal record.
  await expect(page.getByText(/New PR! Bench Press/).first()).toBeVisible();
  await expect(bench.getByText("Set 1")).toBeVisible();
  await expect(bench.getByText("135 x 5")).toBeVisible();
  await expect(page.getByText("1 / 2 exercises")).toBeVisible();

  await page.getByRole("button", { name: "Finish Workout" }).click();
  await expect(page.getByText("Workout complete! Great work.")).toBeVisible();
  await expect(page.getByText("Completed", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Complete Set" })).toHaveCount(0);
});

test("a client logs body weight and steps", async ({ page }) => {
  const { email } = await newClientWithDailyProgram();
  await signIn(page, email);
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.goto("/log");
  await expect(page.getByRole("heading", { name: "Log" })).toBeVisible();
  await page.getByPlaceholder("Weight").fill("182.4");
  await page.getByRole("button", { name: "Save" }).first().click();
  await expect(page.getByText("Weight logged")).toBeVisible();
  await expect(page.getByText("Last logged: 182.4")).toBeVisible();

  await page.getByPlaceholder("Steps").fill("8421");
  await page.getByRole("button", { name: "Save" }).nth(1).click();
  await expect(page.getByText("Steps logged")).toBeVisible();
  await expect(page.getByText("8,421 logged")).toBeVisible();
});

test("a client with no program sees an honest empty state", async ({ page }) => {
  // Seeded client 5 is brand new; this one has no program at all.
  const { apiAs, apiPost, uniqueEmail, COACH, PASSWORD } = await import("./helpers");
  const coach = await apiAs(COACH);
  const email = uniqueEmail("e2e-noprogram");
  await apiPost(coach, "/api/coach/clients", {
    email, temporaryPassword: PASSWORD, firstName: "Nope", lastName: "Program", fitnessGoal: "FAT_LOSS",
  });
  await coach.dispose();

  await signIn(page, email);
  await page.goto("/workout/today");
  await expect(page.getByRole("heading", { name: "Nothing scheduled" })).toBeVisible();
  await expect(page.getByText("Your coach hasn't assigned a program yet.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Finish Workout" })).toHaveCount(0);
});
