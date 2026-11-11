import { defineConfig, devices } from "@playwright/test";
import { SHEET_PORT } from "./vite.config.ts";

export default defineConfig({
  testDir: import.meta.dirname,
  testMatch: "screenshots.spec.ts",
  outputDir: new URL("../../../build/screenshots/ui/results", import.meta.url)
    .pathname,
  reporter: "list",
  use: {
    ...devices["Desktop Chrome"],
    baseURL: `http://localhost:${SHEET_PORT}`,
  },
  webServer: {
    command: "pnpm exec vite",
    cwd: import.meta.dirname,
    url: `http://localhost:${SHEET_PORT}`,
    reuseExistingServer: true,
  },
});
