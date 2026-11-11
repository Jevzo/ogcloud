import { layout, themeNames } from "@ogcloud/tokens";
import { test } from "@playwright/test";
import { LOGIN_DEV_PORT } from "../vite.login.config.ts";

// The login pages' steps against fixtures (`?step=`, `?error`), in both themes.
const OUT = new URL("../../../build/screenshots/web/", import.meta.url)
  .pathname;
const STEPS = [
  "credentials",
  "credentials&error",
  "totp",
  "passkey",
  "enrolment",
] as const;

for (const theme of themeNames) {
  for (const step of STEPS) {
    test(`login ${step}, ${theme}`, async ({ page }) => {
      await page.addInitScript(
        (chosen) =>
          localStorage.setItem(
            "ogcloud.preferences",
            JSON.stringify({ theme: chosen, density: "compact" }),
          ),
        theme,
      );
      await page.setViewportSize(layout.reviewViewports[0]);
      await page.goto(`http://localhost:${LOGIN_DEV_PORT}/login/?step=${step}`);
      await page.getByRole("heading").first().waitFor();
      await page.evaluate(() => document.fonts.ready);
      await page.screenshot({
        path: `${OUT}login-${step.replace("&", "-")}-${theme}.png`,
      });
      if (step === "enrolment") {
        await page.getByRole("button", { name: /Authenticator app/ }).click();
        await page.getByRole("img", { name: /Scan this code/ }).waitFor();
        await page.screenshot({
          path: `${OUT}login-enrolment-app-${theme}.png`,
        });
      }
    });
  }
}
