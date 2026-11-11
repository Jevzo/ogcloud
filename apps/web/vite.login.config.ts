import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

/** The API serves this build at /login (D-0212); its image carries `dist-login`. */
export const LOGIN_DEV_PORT = 5174;

export default defineConfig({
  root: `${import.meta.dirname}/login`,
  base: "/login/",
  plugins: [react(), tailwindcss()],
  // The entry lives in login/ but its code in the app's src/, shared with the dashboard.
  resolve: {
    alias: [{ find: /^\/src\//, replacement: `${import.meta.dirname}/src/` }],
  },
  publicDir: false,
  // Its own dependency cache: the app's dev server runs beside it and would otherwise rewrite the shared one.
  cacheDir: `${import.meta.dirname}/node_modules/.vite-login`,
  server: { port: LOGIN_DEV_PORT, strictPort: true },
  build: {
    outDir: `${import.meta.dirname}/dist-login`,
    emptyOutDir: true,
    assetsDir: "assets",
  },
});
