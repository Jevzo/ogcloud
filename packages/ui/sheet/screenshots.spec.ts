import { layout, themeNames } from "@ogcloud/tokens";
import { type Page, test } from "@playwright/test";

// Screenshots for review (AGENTS.md §13), at the smallest layout and a large monitor (D-0170, D-0231).
const OUT = new URL("../../../build/screenshots/ui/", import.meta.url).pathname;
const VIEWPORTS = layout.reviewViewports;
const THEMES = themeNames;
const DENSITIES = ["compact", "comfortable"] as const;

async function open(page: Page, theme: string, density: string) {
  await page.goto(`/?theme=${theme}&density=${density}`);
  await page.evaluate(() => document.fonts.ready);
}

for (const theme of THEMES) {
  for (const density of DENSITIES) {
    for (const viewport of VIEWPORTS) {
      test(`sheet, ${theme}, ${density}, ${viewport.name}`, async ({
        page,
      }) => {
        await page.setViewportSize(viewport);
        await open(page, theme, density);
        await page.screenshot({
          path: `${OUT}sheet-${theme}-${density}-${viewport.name}.png`,
          fullPage: true,
        });
        // Each section on its own as well, at full scale for review.
        const sections = page.locator("main > section");
        for (let index = 0; index < (await sections.count()); index++) {
          const section = sections.nth(index);
          const title = await section.locator("h2").first().textContent();
          const slug = (title ?? String(index))
            .toLowerCase()
            .replaceAll(/\W+/g, "-");
          await section.screenshot({
            path: `${OUT}sections/${theme}-${density}-${viewport.name}-${slug}.png`,
          });
        }
      });
    }
  }

  test(`open overlays, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, theme, "compact");
    const shoot = async (
      name: string,
      locator: ReturnType<Page["getByRole"]>,
    ) => {
      await locator.click();
      await page.waitForTimeout(400);
      await page.screenshot({ path: `${OUT}overlay-${theme}-${name}.png` });
      await page.keyboard.press("Escape");
      await page.waitForTimeout(250);
    };
    await shoot(
      "select",
      page.getByRole("combobox", { name: "Default group" }),
    );
    await shoot(
      "combobox",
      page.getByRole("combobox", { name: "Click target" }),
    );
    await shoot("multiselect", page.getByRole("combobox", { name: "Scopes" }));
    await shoot("date", page.getByRole("button", { name: "Rank expires" }));
    await shoot(
      "tag-colour",
      page.getByRole("button", { name: "Colour", exact: true }),
    );
    await page.getByRole("row", { name: /bedwars-e0b2d4/ }).hover();
    await page.screenshot({ path: `${OUT}overlay-${theme}-row-hover.png` });
    await shoot(
      "row-menu",
      page
        .getByRole("row", { name: /bedwars-e0b2d4/ })
        .getByRole("button", { name: "More actions" }),
    );
    await shoot("dialog", page.getByRole("button", { name: "Open a dialog" }));
    await page.getByRole("button", { name: "Show a toast" }).click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}overlay-${theme}-toast.png` });
  });
}
