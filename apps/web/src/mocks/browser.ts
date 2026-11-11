import { faker } from "@faker-js/faker";
import {
  getGroupDeletedMockHandler,
  getGroupGetMockHandler,
  getGroupListMockHandler,
  getMeGetMockHandler,
  getOgCloudAPIMock,
  getOverviewGetMockHandler,
  getOverviewHistoryMockHandler,
  getRuntimeListMockHandler,
  getServerGetMockHandler,
  getServerListMockHandler,
} from "@ogcloud/api-client/mocks";
import { setupWorker } from "msw/browser";
import {
  deletedGroups,
  groupById,
  groups,
  loadHistory,
  me,
  overview,
  runtimeProfiles,
  serverById,
  servers,
} from "./fixtures.ts";

// The generated mocks draw from faker; one seed keeps every screenshot run identical.
const SEED = 20261111;

export async function startMocks(): Promise<void> {
  faker.seed(SEED);
  const worker = setupWorker(
    getMeGetMockHandler(me),
    getOverviewGetMockHandler(overview),
    getOverviewHistoryMockHandler(loadHistory),
    getServerListMockHandler([...servers]),
    getGroupListMockHandler(groups),
    getGroupDeletedMockHandler(deletedGroups),
    getGroupGetMockHandler(({ params }) => groupById(String(params.id))),
    getRuntimeListMockHandler(runtimeProfiles),
    getServerGetMockHandler(({ params }) => serverById(String(params.id))),
    ...getOgCloudAPIMock(),
  );
  await worker.start({ quiet: true });
}
