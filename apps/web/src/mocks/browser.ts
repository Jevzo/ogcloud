import { faker } from "@faker-js/faker";
import type { TextPreviewRequest } from "@ogcloud/api-client";
import {
  getGroupDeletedMockHandler,
  getGroupGetMockHandler,
  getGroupListMockHandler,
  getMeGetMockHandler,
  getNetworkGetMockHandler,
  getNpcGetMockHandler,
  getNpcListMockHandler,
  getOgCloudAPIMock,
  getOverviewGetMockHandler,
  getOverviewHistoryMockHandler,
  getPermissionGroupGetMockHandler,
  getPermissionGroupListMockHandler,
  getPlayerGetMockHandler,
  getPlayerKnownMockHandler,
  getPlayerOnlineMockHandler,
  getRuntimeListMockHandler,
  getServerGetMockHandler,
  getServerListMockHandler,
  getTemplateListMockHandler,
  getTextPreviewMockHandler,
} from "@ogcloud/api-client/mocks";
import { setupWorker } from "msw/browser";
import {
  deletedGroups,
  groupById,
  groups,
  knownPlayers,
  loadHistory,
  me,
  networkSettings,
  npcById,
  npcs,
  onlinePlayers,
  overview,
  permissionGroupById,
  permissionGroups,
  playerById,
  previewOf,
  runtimeProfiles,
  serverById,
  servers,
  templatesOf,
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
    getTemplateListMockHandler(({ params }) =>
      templatesOf(String(params.groupId)),
    ),
    getNetworkGetMockHandler(networkSettings),
    getNpcListMockHandler(npcs),
    getNpcGetMockHandler(({ params }) => npcById(String(params.id))),
    getPlayerOnlineMockHandler({ items: onlinePlayers, nextCursor: null }),
    getPlayerKnownMockHandler({ items: knownPlayers, nextCursor: null }),
    getPlayerGetMockHandler(({ params }) => playerById(String(params.uuid))),
    getPermissionGroupListMockHandler(permissionGroups),
    getPermissionGroupGetMockHandler(({ params }) =>
      permissionGroupById(String(params.id)),
    ),
    getTextPreviewMockHandler(async ({ request }) =>
      previewOf((await request.json()) as TextPreviewRequest),
    ),
    getServerGetMockHandler(({ params }) => serverById(String(params.id))),
    ...getOgCloudAPIMock(),
  );
  await worker.start({ quiet: true });
}
