import { defineConfig } from "orval";

// OgCloud's REST client, generated from the contract springdoc writes (D-0011, D-0163); `mise run gen` refreshes it.
export default defineConfig({
  ogcloud: {
    input: { target: "../../contracts/openapi/openapi.yaml" },
    output: {
      target: "src/generated/ogcloud.ts",
      client: "fetch",
      mode: "single",
      override: {
        mutator: { path: "src/request.ts", name: "request" },
      },
    },
  },
});
