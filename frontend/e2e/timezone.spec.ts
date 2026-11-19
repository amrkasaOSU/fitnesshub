import { expect, test } from "@playwright/test";
import { API, newClientWithDailyProgram, signIn } from "./helpers";

test.use({ timezoneId: "America/New_York" });

test("signing in records the browser's timezone so 'today' matches the client's day", async ({ page }) => {
  const { email } = await newClientWithDailyProgram();
  await signIn(page, email);
  await expect(page).toHaveURL(/\/dashboard$/);

  await expect
    .poll(async () => {
      const res = await page.request.get(`${API}/api/users/me`);
      return (await res.json()).data.timezone;
    })
    .toBe("America/New_York");
});
