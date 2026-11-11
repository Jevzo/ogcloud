import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

/** Serves the component sheet that the screenshot run captures (AGENTS.md §13). */
export const SHEET_PORT = 5180;

export default defineConfig({
  root: import.meta.dirname,
  plugins: [react(), tailwindcss()],
  server: { port: SHEET_PORT, strictPort: true },
});
