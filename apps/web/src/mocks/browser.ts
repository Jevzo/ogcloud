import { faker } from "@faker-js/faker";
import type { SignInRulesView, TextPreviewRequest } from "@ogcloud/api-client";
import {
  getGroupDeletedMockHandler,
  getGroupGetMockHandler,
  getGroupListMockHandler,
  getJobListMockHandler,
  getLogAuditMockHandler,
  getLogScalingMockHandler,
  getMeGetMockHandler,
  getMeListLoginsMockHandler,
  getMeListPasskeysMockHandler,
  getNetworkGetMockHandler,
  getNpcGetMockHandler,
  getNpcListMockHandler,
  getOauthClientListMockHandler,
  getOgCloudAPIMock,
  getOverviewGetMockHandler,
  getOverviewHistoryMockHandler,
  getPermissionGroupGetMockHandler,
  getPermissionGroupListMockHandler,
  getPlayerGetMockHandler,
  getPlayerKnownMockHandler,
  getPlayerOnlineMockHandler,
  getRoleListMockHandler,
  getRolePermissionsMockHandler,
  getRuntimeListMockHandler,
  getServerGetMockHandler,
  getServerListMockHandler,
  getSetupStatusMockHandler,
  getSignInRuleGetMockHandler,
  getSignInRuleUpdateMockHandler,
  getTemplateListMockHandler,
  getTextPreviewMockHandler,
  getUserGetMockHandler,
  getUserListMockHandler,
} from "@ogcloud/api-client/mocks";
import { setupWorker } from "msw/browser";
import {
  ALL_PERMISSIONS,
  auditEntries,
  deletedGroups,
  groupById,
  groups,
  jobs,
  knownPlayers,
  loadHistory,
  logins,
  me,
  networkSettings,
  npcById,
  npcs,
  oauthClients,
  onlinePlayers,
  overview,
  passkeys,
  permissionGroupById,
  permissionGroups,
  playerById,
  previewOf,
  roles,
  runtimeProfiles,
  serverById,
  servers,
  templatesOf,
  userById,
  users,
} from "./fixtures.ts";

// The generated mocks draw from faker; one seed keeps every screenshot run identical.
const SEED = 20261111;

// Kept for the page's lifetime, so switching a sign-in rule shows on the next read.
let signInRules: SignInRulesView = { requireMinecraftLink: false };

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
    // `?eulaPending` shows a network whose owner has not accepted the EULA yet, for the setup's screenshots.
    getNetworkGetMockHandler(() =>
      new URLSearchParams(window.location.search).has("eulaPending")
        ? { ...networkSettings, eulaAcceptedAt: null }
        : networkSettings,
    ),
    getSetupStatusMockHandler({ pending: true }),
    getNpcListMockHandler(npcs),
    getLogAuditMockHandler({ items: auditEntries, nextCursor: null }),
    getLogScalingMockHandler({
      items: overview.recentScaling,
      nextCursor: null,
    }),
    getJobListMockHandler({ items: jobs, nextCursor: null }),
    getNpcGetMockHandler(({ params }) => npcById(String(params.id))),
    getPlayerOnlineMockHandler({ items: onlinePlayers, nextCursor: null }),
    getPlayerKnownMockHandler(({ request }) => {
      // Players are found by the start of their name, as the API does.
      const name =
        new URL(request.url).searchParams.get("name")?.toLowerCase() ?? "";
      return {
        items: knownPlayers.filter((player) =>
          player.name.toLowerCase().startsWith(name),
        ),
        nextCursor: null,
      };
    }),
    getRoleListMockHandler(roles),
    getRolePermissionsMockHandler(ALL_PERMISSIONS),
    getUserListMockHandler({ items: users, nextCursor: null }),
    // Before the user handler, whose /users/{id} would take "sign-in-rules" for an id.
    getSignInRuleGetMockHandler(() => signInRules),
    getSignInRuleUpdateMockHandler(async ({ request }) => {
      signInRules = (await request.json()) as SignInRulesView;
      return signInRules;
    }),
    getUserGetMockHandler(({ params }) => userById(String(params.id))),
    getOauthClientListMockHandler(oauthClients),
    getMeListPasskeysMockHandler(passkeys),
    getMeListLoginsMockHandler(logins),
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
