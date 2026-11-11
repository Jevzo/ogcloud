import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

/** `vite --mode mocks` answers every API call from msw, for screenshots and for building pages without a cluster. */
export const MOCKS_MODE = "mocks";
export const DEV_PORT = 5173;

export default defineConfig(({ mode }) => ({
  plugins: [react(), tailwindcss()],
  // The msw worker exists only in mocks mode; production builds ship no public folder.
  publicDir: mode === MOCKS_MODE ? "mocks/public" : false,
  server: { port: DEV_PORT, strictPort: true },
  build: { sourcemap: true },
}));
