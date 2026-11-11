import { faker } from "@faker-js/faker";
import {
  getMeGetMockHandler,
  getOgCloudAPIMock,
  getOverviewGetMockHandler,
  getOverviewHistoryMockHandler,
  getServerGetMockHandler,
  getServerListMockHandler,
} from "@ogcloud/api-client/mocks";
import { setupWorker } from "msw/browser";
import { loadHistory, me, overview, serverById, servers } from "./fixtures.ts";

// The generated mocks draw from faker; one seed keeps every screenshot run identical.
const SEED = 20261111;

export async function startMocks(): Promise<void> {
  faker.seed(SEED);
  const worker = setupWorker(
    getMeGetMockHandler(me),
    getOverviewGetMockHandler(overview),
    getOverviewHistoryMockHandler(loadHistory),
    getServerListMockHandler([...servers]),
    getServerGetMockHandler(({ params }) => serverById(String(params.id))),
    ...getOgCloudAPIMock(),
  );
  await worker.start({ quiet: true });
}
