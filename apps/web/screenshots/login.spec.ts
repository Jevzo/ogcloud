import { layout, themeNames } from "@ogcloud/tokens";
import { test } from "@playwright/test";
import { LOGIN_DEV_PORT } from "../vite.login.config.ts";

// The login pages' steps against fixtures (`?step=`, `?error`), in both themes; the name and the path after /login/.
const OUT = new URL("../../../build/screenshots/web/", import.meta.url)
  .pathname;
const PAGES = [
  ["credentials", "?step=credentials"],
  ["credentials-error", "?step=credentials&error"],
  ["totp", "?step=totp"],
  ["passkey", "?step=passkey"],
  ["enrolment", "?step=enrolment"],
  ["add-passkey", "passkeys/new?step=done"],
] as const;

for (const theme of themeNames) {
  for (const [name, path] of PAGES) {
    test(`login ${name}, ${theme}`, async ({ page }) => {
      await page.addInitScript(
        (chosen) =>
          localStorage.setItem(
            "ogcloud.preferences",
            JSON.stringify({ theme: chosen, density: "compact" }),
          ),
        theme,
      );
      await page.setViewportSize(layout.reviewViewports[0]);
      await page.goto(`http://localhost:${LOGIN_DEV_PORT}/login/${path}`);
      await page.getByRole("heading").first().waitFor();
      await page.evaluate(() => document.fonts.ready);
      await page.screenshot({
        path: `${OUT}login-${name}-${theme}.png`,
      });
      if (name === "enrolment") {
        await page.getByRole("button", { name: /Authenticator app/ }).click();
        await page.getByRole("img", { name: /Scan this code/ }).waitFor();
        await page.screenshot({
          path: `${OUT}login-enrolment-app-${theme}.png`,
        });
      }
    });
  }
}
