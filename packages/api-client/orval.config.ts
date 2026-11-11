import { defineConfig } from "orval";

// OgCloud's REST client, generated from the contract springdoc writes (D-0011, D-0163); `mise run gen` refreshes it.
// The outputs share one model folder: the fetch client for scripts and tests, the TanStack Query hooks with their msw
// mocks for the dashboard, and zod schemas for its forms (D-0211).
const input = { target: "../../contracts/openapi/openapi.yaml" };
const schemas = "src/generated/model";

export default defineConfig({
  ogcloud: {
    input,
    output: {
      target: "src/generated/ogcloud.ts",
      schemas,
      client: "fetch",
      mode: "single",
      override: {
        mutator: { path: "src/request.ts", name: "request" },
      },
    },
  },
  query: {
    input,
    output: {
      target: "src/generated/query.ts",
      schemas,
      client: "react-query",
      httpClient: "fetch",
      mode: "split",
      mock: { generators: [{ type: "msw", useExamples: true }] },
      override: {
        mutator: { path: "src/react/connection.ts", name: "useApiRequest" },
      },
    },
  },
  zod: {
    input,
    output: {
      target: "src/generated/zod.ts",
      client: "zod",
      mode: "single",
    },
  },
});
