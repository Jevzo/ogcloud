import { layout, themeNames } from "@ogcloud/tokens";
import { type Page, test } from "@playwright/test";
import {
  MOCK_NOW,
  onlinePlayers,
  servers,
  users,
} from "../src/mocks/fixtures.ts";

// The dashboard against orval's mocks (D-0211), at the smallest layout and a large monitor (D-0170, D-0216).
const OUT = new URL("../../../build/screenshots/web/", import.meta.url)
  .pathname;
const VIEWPORTS = layout.reviewViewports;
const THEMES = themeNames;
// A busy bedwars server for the server page.
const SERVER = servers.find((server) => server.gameState === "INGAME")?.id;
const PAGES = [
  "/",
  "/servers",
  `/servers/${SERVER}`,
  "/groups",
  "/groups/new",
  "/groups/bedwars",
  "/players",
  `/players/${onlinePlayers[1]?.uuid}`,
  "/permissions",
  "/permissions/owner",
  "/network",
  "/npcs",
  "/npcs/0195b1d2-7c4e-7a10-9f3e-0000000a0001",
  "/users",
  `/users/${users[2]?.id}`,
  "/roles",
  "/roles/builder",
  "/clients",
  "/clients/oc_2f6b9d1c8e4a",
  "/privacy",
  "/account",
] as const;

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

const slug = (path: string) =>
  path === "/"
    ? "overview"
    : path.startsWith("/servers/")
      ? "server"
      : path.startsWith("/players/")
        ? "player"
        : path.startsWith("/npcs/")
          ? "npc"
          : path.startsWith("/users/")
            ? "user"
            : path.startsWith("/roles/")
              ? "role"
              : path.startsWith("/clients/")
                ? "client"
                : path.slice(1).replaceAll("/", "-");

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

  test(`unsaved edits, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, "/groups/bedwars", theme);
    await page.getByLabel("Fewest servers").fill("3");
    await page.getByLabel("Most servers").fill("24");
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}save-bar-${theme}.png` });
  });

  test(`a toast, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, "/groups/bedwars", theme);
    await page.getByLabel("Fewest servers").fill("3");
    await page.getByRole("button", { name: "Save changes" }).click();
    await page.getByRole("status").filter({ hasText: "Saved" }).waitFor();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}toast-${theme}.png` });
  });

  test(`player dialogs, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, `/players/${onlinePlayers[1]?.uuid}`, theme);
    await page.getByRole("button", { name: "Set rank" }).click();
    await page.getByRole("dialog").waitFor();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}rank-dialog-${theme}.png` });
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Kick" }).click();
    await page.getByRole("dialog").waitFor();
    await page.getByLabel("Message").fill("<red>Back in 5 minutes");
    await page.waitForTimeout(800);
    await page.screenshot({ path: `${OUT}kick-dialog-${theme}.png` });
  });

  test(`templates, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, "/groups/smp", theme);
    await page.getByRole("tab", { name: "Templates" }).click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}templates-${theme}.png` });
  });

  test(`administration flows, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, "/account", theme);
    await page.getByRole("tab", { name: "Security" }).click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}account-security-${theme}.png` });
    await page.getByRole("button", { name: "Remove" }).first().click();
    await page.getByRole("dialog").waitFor();
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${OUT}account-remove-totp-${theme}.png` });
  });

  test(`privacy search and new client, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, "/privacy", theme);
    await page.getByRole("searchbox", { name: "Player name" }).fill("ki");
    await page.getByRole("searchbox", { name: "Username or email" }).fill("j");
    await page.waitForTimeout(700);
    await page.screenshot({ path: `${OUT}privacy-search-${theme}.png` });
    await page.goto("/clients");
    await page.getByRole("button", { name: "Create client" }).click();
    await page.getByRole("dialog").waitFor();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}clients-create-${theme}.png` });
  });

  test(`a new user, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, "/users", theme);
    await page.getByRole("button", { name: "Add user" }).click();
    await page.getByRole("button", { name: "Generate" }).click();
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${OUT}users-create-${theme}.png` });
  });

  test(`setup wizard, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await page.addInitScript(
      (chosen) =>
        localStorage.setItem(
          "ogcloud.preferences",
          JSON.stringify({ theme: chosen, density: "compact" }),
        ),
      theme,
    );
    await page.goto("/setup");
    await page.getByLabel("Setup token", { exact: true }).waitFor();
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: `${OUT}setup-token-${theme}.png` });
    await page
      .getByLabel("Setup token", { exact: true })
      .fill("Hk3x9QmZ0tLw7pVb2nRc5sYd8fJg1aE4");
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByLabel("Username").fill("mira");
    await page.getByLabel("Email").fill("mira@ogwars.io");
    await page.screenshot({ path: `${OUT}setup-owner-${theme}.png` });
    await page.goto("/welcome?eulaPending");
    await page.getByRole("checkbox").waitFor();
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${OUT}setup-eula-${theme}.png` });
    await page.goto("/welcome");
    await page.getByRole("button", { name: "Continue" }).click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}setup-groups-${theme}.png` });
  });

  test(`activity, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, "/activity", theme);
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}activity-${theme}.png` });
    await page.getByRole("tab", { name: "Jobs" }).click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}jobs-${theme}.png` });
  });

  test(`a typed confirmation, ${theme}`, async ({ page }) => {
    await page.setViewportSize(VIEWPORTS[0]);
    await open(page, `/servers/${SERVER}`, theme);
    await page.getByRole("button", { name: "More actions" }).click();
    await page.getByRole("menuitem", { name: "Kill" }).click();
    await page.getByRole("dialog").waitFor();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}kill-dialog-${theme}.png` });
  });
}
