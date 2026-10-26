import { defineConfig } from "i18next-cli";

export default defineConfig({
  locales: ["en"],
  extract: {
    input: ["../../apps/*/src/**/*.{ts,tsx}"],
    output: "src/locales/{{language}}/{{namespace}}.json",
    defaultNS: "common",
  },
});
