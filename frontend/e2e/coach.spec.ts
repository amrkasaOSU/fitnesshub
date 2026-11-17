import { expect, test } from "@playwright/test";
import { COACH, PASSWORD, signIn, uniqueEmail } from "./helpers";

test("a coach adds a client, builds a program and assigns it", async ({ page }) => {
  const email = uniqueEmail("e2e-added");
  const firstName = "Added" + Math.random().toString(36).slice(2, 6);
  const programName = `E2E Strength ${firstName}`;

  await signIn(page, COACH);
  await expect(page).toHaveURL(/\/coach\/dashboard$/);

  // --- add a client through the dialog
  await page.getByRole("link", { name: "Clients" }).first().click();
  await expect(page.getByRole("heading", { name: "Clients" })).toBeVisible();
  await page.getByRole("button", { name: "Add client" }).click();
  const dialog = page.getByRole("dialog", { name: "Add a client" });
  await dialog.getByRole("tab", { name: "Create account" }).click();
  await dialog.getByLabel("First name").fill(firstName);
  await dialog.getByLabel("Last name").fill("Tester");
  await dialog.getByLabel("Email").fill(email);
  await dialog.getByLabel("Temporary password").fill(PASSWORD);
  await dialog.getByRole("combobox", { name: "Primary goal" }).click();
  await page.getByRole("option", { name: "Strength" }).click();
  await dialog.getByRole("button", { name: "Create client account" }).click();
  await expect(page.getByText(`${firstName} added.`)).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();

  // --- build a one-exercise program
  await page.getByRole("link", { name: "Programs" }).first().click();
  await page.getByRole("link", { name: /new program/i }).click();
  await page.getByLabel("Program name").fill(programName);
  await page.getByRole("button", { name: "Exercise" }).first().click();
  await page.getByRole("button", { name: "Create program" }).click();
  await expect(page.getByText("Program created")).toBeVisible();
  await expect(page.getByRole("heading", { name: programName })).toBeVisible();

  // --- assign it to the new client
  await page.getByRole("combobox").first().click();
  await page.getByRole("option", { name: `${firstName} Tester` }).click();
  await page.getByRole("button", { name: "Assign" }).click();
  await expect(page.getByText(`Assigned to ${firstName}`)).toBeVisible();
});

test("the coach dashboard surfaces clients who need attention", async ({ page }) => {
  await signIn(page, COACH);
  await expect(page.getByRole("heading", { name: "Coach Dashboard" })).toBeVisible();
  // Seeded client 3 is built to trip attention flags.
  await expect(page.getByText("Riley").first()).toBeVisible();
});
