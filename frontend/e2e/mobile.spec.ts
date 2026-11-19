import { expect, test } from "@playwright/test";
import { COACH, newClientWithDailyProgram, signIn } from "./helpers";

test("on a phone the client gets a bottom tab bar and no sideways scrolling", async ({ page }) => {
  const { email } = await newClientWithDailyProgram();
  await signIn(page, email);
  await expect(page).toHaveURL(/\/dashboard$/);

  const tabBar = page.getByRole("navigation", { name: "Primary" });
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

test("on a phone every coach page fits the screen and the inbox is list-then-thread", async ({ page }) => {
  await signIn(page, COACH);
  await expect(page).toHaveURL(/\/coach\/dashboard$/);

  for (const path of ["/coach/dashboard", "/coach/clients", "/coach/programs", "/coach/checkins", "/coach/messages"]) {
    await page.goto(path);
    await page.waitForLoadState("networkidle");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, `${path} scrolls sideways by ${overflow}px`).toBeLessThanOrEqual(0);
  }

  // /coach/messages: the list first, the thread only after picking someone.
  const firstConversation = page.getByRole("button", { name: /Alex Morgan/ });
  await expect(firstConversation).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Message" })).toBeHidden();
  await firstConversation.click();
  await expect(page.getByRole("textbox", { name: "Message" })).toBeVisible();
  await expect(firstConversation).toBeHidden();
  await page.getByRole("button", { name: "All conversations" }).click();
  await expect(firstConversation).toBeVisible();
});
