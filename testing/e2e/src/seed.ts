import { randomBytes } from "node:crypto";
import {
  type Call,
  type CreateGroupRequest,
  groupCreate,
  groupList,
  oauthClientCreate,
  rolePermissions,
  serverList,
  setupStatus,
  templateList,
  templateUpload,
} from "@ogcloud/api-client";
import { anonymous, as, asClient, expectStatus } from "./api.ts";
import { secretValue, trustInternalCa } from "./cluster.ts";
import { signIn } from "./login.ts";
import { setUpThroughWizard } from "./setup-wizard.ts";
import { loadState, type SeedState, saveState } from "./state.ts";
import { tarGz } from "./tar.ts";
import { type Target, targetFromEnv } from "./target.ts";
import { eventually } from "./wait.ts";

/** What the suite expects to find; `mise run seed` creates whatever is missing and leaves the rest alone. */
export const GROUPS = {
  proxy: "proxy",
  lobby: "lobby",
  legacy: "legacy",
} as const;

// CI's k3d node has four CPUs, most of them requested by the platform; at 250m each, a crashing group's server could
// no longer be scheduled once the lobby had scaled up, so it never failed and was never paused.
const CPU_REQUEST = "100m";

const SCALING = {
  scaleUpThreshold: 0.8,
  scaleDownThreshold: 0.2,
  cooldownSeconds: 30,
};

const GROUP_REQUESTS: readonly CreateGroupRequest[] = [
  {
    id: GROUPS.proxy,
    type: "PROXY",
    settings: {
      profileId: "velocity-4.3.0",
      scaling: {
        ...SCALING,
        minOnline: 1,
        maxInstances: 2,
        playersPerServer: 100,
      },
      drainTimeoutSeconds: 30,
      maintenance: false,
      resources: {
        memoryRequest: "512Mi",
        memoryLimit: "512Mi",
        cpuRequest: CPU_REQUEST,
      },
      jvmFlags: "",
    },
  },
  // The network's default group (V1's seed data); few players per server, so a handful of bots makes it scale.
  {
    id: GROUPS.lobby,
    type: "DYNAMIC",
    settings: {
      profileId: "paper-26.3",
      scaling: {
        ...SCALING,
        minOnline: 1,
        maxInstances: 3,
        playersPerServer: 4,
      },
      drainTimeoutSeconds: 30,
      maintenance: false,
      resources: {
        memoryRequest: "1536Mi",
        memoryLimit: "1536Mi",
        cpuRequest: CPU_REQUEST,
      },
      jvmFlags: "",
    },
  },
  {
    id: GROUPS.legacy,
    type: "DYNAMIC",
    settings: {
      profileId: "pandaspigot-1.8.8",
      scaling: {
        ...SCALING,
        minOnline: 1,
        maxInstances: 1,
        playersPerServer: 10,
      },
      drainTimeoutSeconds: 30,
      maintenance: false,
      resources: {
        memoryRequest: "768Mi",
        memoryLimit: "768Mi",
        cpuRequest: CPU_REQUEST,
      },
      jvmFlags: "",
    },
  },
];

const READY_TIMEOUT_MS = 900_000;

/** The lobby's template; the scenarios find the marker in its servers' files. */
export const LOBBY_MARKER = "ogcloud-e2e.txt";

const OWNER = { username: "e2e-owner", email: "owner@ogcloud.localhost" };

export async function seed(target: Target): Promise<SeedState> {
  let state = await ensureOwner(target, await loadState(target));
  const owner = await signIn(target, state.owner);
  if (owner.totpSecret === undefined) {
    throw new Error(
      "The owner signed in without an authenticator, which D-0097 requires of owners",
    );
  }
  state = { ...state, owner: { ...state.owner, totpSecret: owner.totpSecret } };
  await saveState(target, state);

  const client =
    state.client ?? (await createClient(target, owner.accessToken));
  state = { ...state, client };
  await saveState(target, state);
  const call = asClient(target, client);

  const existing = new Set(
    expectStatus(await groupList(call), 200, "Listing the groups").map(
      (group) => group.id,
    ),
  );
  for (const request of GROUP_REQUESTS.filter(
    (group) => !existing.has(group.id),
  )) {
    expectStatus(
      await groupCreate(request, call),
      201,
      `Creating group ${request.id}`,
    );
    console.log(`Created group ${request.id}`);
  }
  if (
    expectStatus(
      await templateList(GROUPS.lobby, call),
      200,
      "Listing the lobby's templates",
    ).length === 0
  ) {
    const archive = tarGz({ [LOBBY_MARKER]: "Seeded by mise run seed.\n" });
    expectStatus(
      await templateUpload(GROUPS.lobby, new Blob([archive]), call),
      201,
      "Uploading the lobby's template",
    );
    console.log(`Uploaded the ${GROUPS.lobby} template`);
  }
  // Scenarios start from a running network; a fresh cluster pulls the bundles and starts its servers first.
  await awaitRunningGroups(
    call,
    GROUP_REQUESTS.map(({ id }) => id),
  );
  return state;
}

/** Waits until each of [groups] runs a server, failing with the servers' states otherwise. */
export async function awaitRunningGroups(
  call: Call,
  groups: readonly string[],
): Promise<void> {
  for (const id of groups) {
    const servers = async () =>
      expectStatus(
        await serverList({ groupId: id }, call),
        200,
        `Listing the servers of ${id}`,
      );
    try {
      await eventually(
        `group ${id} to run a server`,
        async () =>
          (await servers()).some((server) => server.state === "RUNNING")
            ? true
            : undefined,
        READY_TIMEOUT_MS,
      );
    } catch (error) {
      const states = (await servers()).map(
        (server) => `${server.id} ${server.state} since ${server.stateSince}`,
      );
      throw new Error(`${id} runs no server: ${states.join("; ") || "none"}`, {
        cause: error,
      });
    }
  }
  console.log("Every group runs a server");
}

// The suite's machine client holds every permission; only owners may create one (D-0094, D-0108).
async function createClient(
  target: Target,
  ownerToken: string,
): Promise<NonNullable<SeedState["client"]>> {
  const call = as(target, ownerToken);
  const scopes = expectStatus(
    await rolePermissions(call),
    200,
    "Listing the permissions",
  );
  const created = expectStatus(
    await oauthClientCreate({ name: "OgCloud end-to-end suite", scopes }, call),
    201,
    "Creating the suite's machine client",
  );
  return {
    clientId: created.clientId,
    clientSecret: created.clientSecret,
    scopes: created.scopes,
  };
}

// The first owner comes from the setup token bootstrap put into Infisical (D-0094), through the dashboard's setup
// wizard; later runs reuse the saved login.
async function ensureOwner(
  target: Target,
  state: SeedState | undefined,
): Promise<SeedState> {
  const pending = expectStatus(
    await setupStatus(anonymous(target)),
    200,
    "Reading the setup state",
  ).pending;
  if (!pending) {
    if (state === undefined) {
      throw new Error(
        `${target.origin} already has an owner, and ${target.stateDir} holds no login for it`,
      );
    }
    return state;
  }
  const setupToken = await secretValue(
    target,
    "ogcloud-api-config",
    "ogcloud.auth.setup-token",
  );
  const owner = await setUpThroughWizard(
    target,
    { ...OWNER, password: randomBytes(24).toString("base64url") },
    setupToken,
  );
  console.log(`Created the owner ${owner.username}`);
  const created: SeedState = { owner };
  await saveState(target, created);
  return created;
}

if (import.meta.main) {
  const target = targetFromEnv();
  await trustInternalCa(target);
  await seed(target);
  console.log("Seeded");
}
