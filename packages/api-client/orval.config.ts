import { defineConfig } from "orval";

// OgCloud's REST client, generated from the contract springdoc writes (D-0011, D-0163); `mise run gen` refreshes it.
// The outputs share one model folder: the fetch client for scripts and tests, the TanStack Query hooks with their msw
// mocks for the dashboard, and zod schemas for its forms (D-0211). scripts/generate.ts picks the output folder.
const input = { target: "../../contracts/openapi/openapi.yaml" };
const out = process.env.OGCLOUD_API_CLIENT_OUT ?? "src/generated";
const schemas = `${out}/model`;

export default defineConfig({
  ogcloud: {
    input,
    output: {
      target: `${out}/ogcloud.ts`,
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
      target: `${out}/query.ts`,
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
      target: `${out}/zod.ts`,
      client: "zod",
      mode: "single",
    },
  },
});
