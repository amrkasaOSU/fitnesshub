import { expect, test } from "@playwright/test";
import { newClientWithDailyProgram, signIn } from "./helpers";

test("on a phone the client gets a bottom tab bar and no sideways scrolling", async ({ page }) => {
  const { email } = await newClientWithDailyProgram();
  await signIn(page, email);
  await expect(page).toHaveURL(/\/dashboard$/);

  // The sidebar's nav is hidden below the md breakpoint, so the only visible
  // navigation landmark on a phone is the bottom tab bar.
  const tabBar = page.getByRole("navigation").filter({ visible: true });
  await expect(tabBar).toBeVisible();
  for (const tab of ["Dashboard", "Today", "Log", "Progress", "Messages"]) {
    await expect(tabBar.getByRole("link", { name: tab })).toBeVisible();
  }

  await tabBar.getByRole("link", { name: "Today" }).click();
  await expect(page).toHaveURL(/\/workout\/today$/);
  await expect(page.getByRole("heading", { name: "Full Body" })).toBeVisible();

  for (const path of ["/dashboard", "/workout/today", "/log", "/progress"]) {
    await page.goto(path);
    await page.waitForLoadState("networkidle");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, `${path} scrolls sideways by ${overflow}px`).toBeLessThanOrEqual(0);
  }
});
