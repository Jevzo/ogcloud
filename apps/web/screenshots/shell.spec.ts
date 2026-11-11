import { layout, themeNames } from "@ogcloud/tokens";
import { type Page, test } from "@playwright/test";
import { MOCK_NOW } from "../src/mocks/fixtures.ts";

// The dashboard against orval's mocks (D-0211), at the smallest layout and a large monitor (D-0170, D-0216).
const OUT = new URL("../../../build/screenshots/web/", import.meta.url)
  .pathname;
const VIEWPORTS = layout.reviewViewports;
const THEMES = themeNames;
const PAGES = ["/", "/servers", "/users"] as const;

async function open(
  page: Page,
  path: string,
  theme: string,
  density = "compact",
) {
  await page.addInitScript(
    ([chosenTheme, chosenDensity]) =>
      localStorage.setItem(
        "ogcloud.preferences",
        JSON.stringify({ theme: chosenTheme, density: chosenDensity }),
      ),
    [theme, density],
  );
  await page.clock.setFixedTime(MOCK_NOW);
  await page.goto(path);
  await page.getByRole("navigation").first().waitFor();
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
}

const slug = (path: string) => (path === "/" ? "overview" : path.slice(1));

for (const theme of THEMES) {
  for (const viewport of VIEWPORTS) {
    for (const path of PAGES) {
      test(`${slug(path)}, ${theme}, ${viewport.name}`, async ({ page }) => {
        await page.setViewportSize(viewport);
        await open(page, path, theme);
        await page.screenshot({
          path: `${OUT}${slug(path)}-${theme}-${viewport.name}.png`,
        });
      });
    }
  }

  test(`shell overlays, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, "/", theme);
    await page.keyboard.press("ControlOrMeta+k");
    await page.getByRole("dialog").waitFor();
    await page.keyboard.type("ser");
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}palette-${theme}.png` });
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: /mira/ }).click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}account-menu-${theme}.png` });
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Collapse the sidebar" }).click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}collapsed-${theme}.png` });
  });
}
