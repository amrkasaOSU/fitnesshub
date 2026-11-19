import { expect, test } from "@playwright/test";
import { COACH, signIn } from "./helpers";

test("a failed request shows an error with a working retry, not an endless skeleton", async ({ page }) => {
  await signIn(page, "client1@fitnesshub.local");
  await expect(page).toHaveURL(/\/dashboard$/);

  // Make the dashboard's own data request fail, as if the API were down.
  let fail = true;
  await page.route("**/api/dashboard", (route) =>
    fail
      ? route.fulfill({
          status: 503,
          contentType: "application/json",
          body: JSON.stringify({ error: { code: "UNAVAILABLE", message: "The server is restarting." } }),
        })
      : route.continue()
  );
  await page.reload();

  const alert = page.getByRole("alert").filter({ hasText: "Couldn't load your dashboard" });
  // The provider retries once before giving up, so allow for that.
  await expect(alert).toBeVisible({ timeout: 15_000 });
  await expect(alert).toContainText("The server is restarting.");

  fail = false;
  await alert.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByText("Weekly Adherence")).toBeVisible();
  await expect(alert).toHaveCount(0);
});

test("a failed roster load is not shown as 'no clients yet'", async ({ page }) => {
  await signIn(page, COACH);
  await page.route("**/api/clients", (route) =>
    route.fulfill({ status: 500, contentType: "application/json", body: "{}" })
  );
  await page.goto("/coach/clients");

  await expect(page.getByRole("alert").filter({ hasText: "Couldn't load your clients" })).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(/no clients yet/i)).toHaveCount(0);
});

test("the notification bell and icon-only controls have accessible names", async ({ page }) => {
  await signIn(page, "client1@fitnesshub.local");
  await expect(page.getByRole("link", { name: /^Notifications/ })).toBeVisible();
  await expect(page.getByRole("button", { name: "Account menu" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Dashboard", exact: true }).first()).toHaveAttribute(
    "aria-current",
    "page"
  );

  await page.goto("/progress");
  await expect(page.getByRole("combobox", { name: "Exercise" })).toBeVisible();
});
