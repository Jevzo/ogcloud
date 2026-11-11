import { defineConfig, devices } from "@playwright/test";
import { DEV_PORT, MOCKS_MODE } from "./vite.config.ts";
import { LOGIN_DEV_PORT } from "./vite.login.config.ts";

export default defineConfig({
  testDir: "screenshots",
  outputDir: new URL("../../build/screenshots/web/results", import.meta.url)
    .pathname,
  reporter: "list",
  use: {
    ...devices["Desktop Chrome"],
    baseURL: `http://localhost:${DEV_PORT}`,
  },
  webServer: [
    {
      command: `pnpm exec vite --mode ${MOCKS_MODE}`,
      cwd: import.meta.dirname,
      url: `http://localhost:${DEV_PORT}`,
      reuseExistingServer: true,
    },
    {
      command: `pnpm exec vite --config vite.login.config.ts --mode ${MOCKS_MODE}`,
      cwd: import.meta.dirname,
      url: `http://localhost:${LOGIN_DEV_PORT}/login/`,
      reuseExistingServer: true,
    },
  ],
});
