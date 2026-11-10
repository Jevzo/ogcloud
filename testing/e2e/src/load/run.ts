import { randomBytes } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { join as joinPath } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { fileURLToPath } from "node:url";
import {
  type Call,
  type CreateGroupRequest,
  type GroupView,
  groupCreate,
  groupDelete,
  groupGet,
  groupList,
  groupUpdate,
  networkGet,
  networkUpdate,
  playerOnline,
  serverList,
  serverTransferAll,
  templatePromote,
  templateUpload,
} from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { kubectl } from "../cluster.ts";
import { awaitRunningGroups, GROUPS as SEEDED } from "../seed.ts";
import { type Suite, suite } from "../suite.ts";
import { tarGz } from "../tar.ts";
import { eventually } from "../wait.ts";
import {
  extrapolate,
  type Measured,
  percentile,
  shareWithin,
  type Usage,
} from "./measure.ts";
import { loginHistogram, usage } from "./probe.ts";
import { Swarm } from "./swarm.ts";
import { now } from "./swarm-protocol.ts";

// The load run (T-0609, D-0174, D-0175): two groups made for the run take the bots in waves, which then move from
// one to the other and back. It measures latencies, the gateway's login resolution and the platform's usage, and
// extrapolates to D-0053's target. Everything it changes is put back, even when it fails.

const SETTINGS = {
  players: number("OGCLOUD_LOAD_PLAYERS", 500),
  serversPerGroup: number("OGCLOUD_LOAD_SERVERS_PER_GROUP", 15),
  // Bots that join together, a second apart.
  wave: number("OGCLOUD_LOAD_WAVE", 25),
  // Processes the bots are spread over, so none falls behind its bots' keep-alives.
  botWorkers: number("OGCLOUD_LOAD_BOT_WORKERS", 6),
  // How long the loaded network runs before it is measured, so the metrics server sees it.
  holdSeconds: number("OGCLOUD_LOAD_HOLD_SECONDS", 90),
  serverMemory: process.env.OGCLOUD_LOAD_SERVER_MEMORY ?? "1536Mi",
  // Appended to the profile's own (D-0139), e.g. a smaller heap share.
  jvmFlags: process.env.OGCLOUD_LOAD_JVM_FLAGS ?? "",
};

// D-0175's pass criteria.
const CRITERIA = {
  joinP99Ms: 2000,
  transferP99Ms: 2000,
  loginBoundSeconds: 0.05,
  loginShare: 0.99,
};
// D-0053: 5,000 players on about 300 servers and 20 proxies.
const TARGET = { players: 5000, servers: 320 };
// Deleted groups wait for their purge (D-0043), so every run names its own.
const GROUP_PREFIX = "load-";
// The seeded proxy group grows for the run: two proxies with room and memory for every bot.
const PROXIES = { count: 2, memory: "1536Mi" };
const GROUP_LABEL = "cloud.ogwars.io/group";
const RUN = randomBytes(3).toString("hex");
const GROUPS = {
  first: `${GROUP_PREFIX}${RUN}-a`,
  second: `${GROUP_PREFIX}${RUN}-b`,
} as const;
const SERVERS_NAMESPACE = "ogcloud-servers";
const READY_TIMEOUT_MS = 1_200_000;
const TRANSFER_TIMEOUT_MS = 120_000;
const SETTLE_MS = 60_000;
// A backend's heartbeat plus the proxies' 5-second refresh of the network's counts.
const COUNTS_SETTLE_MS = 15_000;
const NAME_PREFIX = "ld";
const REPORT_DIR = fileURLToPath(
  new URL("../../../../build/load", import.meta.url),
);

// Flat, small worlds, so thirty servers start quickly and hold their players cheaply.
const TEMPLATE = tarGz({
  "server.properties": [
    "level-type=minecraft\\:flat",
    "generate-structures=false",
    "view-distance=4",
    "simulation-distance=4",
    "spawn-protection=0",
    "",
  ].join("\n"),
});

interface Latencies {
  readonly join: number[];
  readonly transfer: number[];
}

async function run(context: Suite): Promise<boolean> {
  const { api, target } = context;
  const existing = expectStatus(
    await groupList(api),
    200,
    "Listing the groups",
  ).map(({ id }) => id);
  const leftovers = existing.filter((id) => id.startsWith(GROUP_PREFIX));
  if (leftovers.length > 0) {
    throw new Error(
      `Groups ${leftovers.join(", ")} remain from an earlier run; delete them first`,
    );
  }
  const network = expectStatus(
    await networkGet(api),
    200,
    "Reading the network",
  );
  const proxy = expectStatus(
    await groupGet(SEEDED.proxy, api),
    200,
    "Reading the proxy group",
  );
  const players: string[] = [];
  const latencies: Latencies = { join: [], transfer: [] };
  const swarm = Swarm.start(SETTINGS.botWorkers);
  let restoreVersion: number | undefined;
  let proxyVersion: number | undefined;
  try {
    proxyVersion = await growProxies(context, proxy);
    const baseline = {
      servers: await runningServers(api),
      usage: await usage(target, target.namespace),
    };
    console.log(
      `Baseline: ${baseline.servers} servers, ${describe(baseline.usage)}`,
    );
    for (const id of Object.values(GROUPS)) {
      await createGroup(api, id);
    }
    restoreVersion = expectStatus(
      await networkUpdate(
        {
          settings: { ...network.settings, defaultGroupId: GROUPS.first },
          version: network.version,
        },
        api,
      ),
      200,
      "Making the first load group the default",
    ).version;
    await awaitServers(api);
    await sleep(SETTLE_MS);
    const idle = {
      servers: await runningServers(api),
      usage: await usage(target, target.namespace),
    };
    const idleGames = await usage(target, SERVERS_NAMESPACE);
    console.log(
      `Idle: ${idle.servers} servers, ${describe(idle.usage)}; game pods ${describe(idleGames)}`,
    );

    const loginsBefore = await loginHistogram(target);
    const failures = await joinInWaves(swarm, players, latencies);
    const lost = await lostSessions(api, players, GROUPS.first);
    await sleep(SETTINGS.holdSeconds * 1000);
    const loaded = {
      players: players.length,
      usage: await usage(target, target.namespace),
    };
    const loadedGames = await usage(target, SERVERS_NAMESPACE);
    console.log(
      `Loaded: ${loaded.players} players, ${describe(loaded.usage)}; game pods ${describe(loadedGames)}`,
    );

    const there = await moveAll(
      api,
      swarm,
      players,
      GROUPS.first,
      GROUPS.second,
      latencies,
    );
    // Proxies count a backend's players from heartbeats, which still show the ones that just left (D-0142).
    await sleep(COUNTS_SETTLE_MS);
    const back = await moveAll(
      api,
      swarm,
      players,
      GROUPS.second,
      GROUPS.first,
      latencies,
    );
    const unmoved = there + back;
    const lostAfterTransfers = await lostSessions(api, players, GROUPS.first);
    const loginsAfter = await loginHistogram(target);

    const measured: Measured = { baseline, idle, loaded };
    const report = {
      settings: SETTINGS,
      criteria: CRITERIA,
      target: TARGET,
      measured,
      games: { idle: idleGames, loaded: loadedGames },
      joins: {
        failed: failures,
        p50: percentile(latencies.join, 50),
        p99: percentile(latencies.join, 99),
      },
      transfers: {
        failed: unmoved,
        p50: percentile(latencies.transfer, 50),
        p99: percentile(latencies.transfer, 99),
      },
      lostSessions: lost + lostAfterTransfers,
      loginShareWithinBound: shareWithin(
        loginsBefore,
        loginsAfter,
        CRITERIA.loginBoundSeconds,
      ),
      logins: loginsAfter.count - loginsBefore.count,
      extrapolated: extrapolate(measured, TARGET),
    };
    const passed =
      report.joins.failed === 0 &&
      report.lostSessions === 0 &&
      report.transfers.failed === 0 &&
      report.joins.p99 < CRITERIA.joinP99Ms &&
      report.transfers.p99 < CRITERIA.transferP99Ms &&
      report.loginShareWithinBound >= CRITERIA.loginShare;
    await writeReport({ ...report, passed });
    return passed;
  } finally {
    await swarm.close();
    if (proxyVersion !== undefined) {
      await setProxies(context, proxy.settings, proxyVersion);
    }
    if (restoreVersion !== undefined) {
      expectStatus(
        await networkUpdate(
          { settings: network.settings, version: restoreVersion },
          api,
        ),
        200,
        "Restoring the default group",
      );
    }
    for (const id of Object.values(GROUPS)) {
      const deleted = await groupDelete(id, api);
      if (
        deleted.status !== 202 &&
        deleted.status !== 204 &&
        deleted.status !== 404
      ) {
        console.error(`Deleting group ${id} answered ${deleted.status}`);
      }
    }
  }
}

/** Gives the proxy group room and memory for every bot and returns its new version. */
async function growProxies(context: Suite, proxy: GroupView): Promise<number> {
  const playersPerServer = Math.ceil((SETTINGS.players * 1.2) / PROXIES.count);
  return await setProxies(
    context,
    {
      ...proxy.settings,
      scaling: {
        ...proxy.settings.scaling,
        minOnline: PROXIES.count,
        maxInstances: PROXIES.count,
        playersPerServer,
      },
      resources: {
        ...proxy.settings.resources,
        memoryRequest: PROXIES.memory,
        memoryLimit: PROXIES.memory,
      },
    },
    proxy.version,
  );
}

/** Saves the proxy group's settings and replaces its proxies, since a pod keeps what it was created with. */
async function setProxies(
  context: Suite,
  settings: GroupView["settings"],
  version: number,
): Promise<number> {
  const { api, target } = context;
  const saved = expectStatus(
    await groupUpdate(SEEDED.proxy, { settings, version }, api),
    200,
    "Updating the proxy group",
  );
  await kubectl(
    target,
    [
      "delete",
      "pods",
      "--selector",
      `${GROUP_LABEL}=${SEEDED.proxy}`,
      "--wait=false",
    ],
    SERVERS_NAMESPACE,
  );
  const { minOnline, playersPerServer } = settings.scaling;
  await eventually(
    `${minOnline} proxies with room for ${playersPerServer} players each`,
    async () => {
      const proxies = expectStatus(
        await serverList({ groupId: SEEDED.proxy }, api),
        200,
        "Listing the proxies",
      ).filter(
        (server) =>
          server.state === "RUNNING" && server.maxPlayers === playersPerServer,
      );
      return proxies.length >= minOnline ? true : undefined;
    },
    READY_TIMEOUT_MS,
  );
  console.log(
    `${minOnline} proxies run with room for ${playersPerServer} players each`,
  );
  return saved.version;
}

async function createGroup(api: Call, id: string): Promise<void> {
  const request: CreateGroupRequest = {
    id,
    type: "DYNAMIC",
    settings: {
      profileId: "paper-26.3",
      scaling: {
        minOnline: SETTINGS.serversPerGroup,
        maxInstances: SETTINGS.serversPerGroup,
        // Room for every bot, so the autoscaler neither adds nor drains servers mid-run.
        playersPerServer: Math.ceil(
          (SETTINGS.players * 1.2) / SETTINGS.serversPerGroup,
        ),
        scaleUpThreshold: 1,
        scaleDownThreshold: 0,
        cooldownSeconds: 30,
      },
      drainTimeoutSeconds: 30,
      maintenance: false,
      resources: {
        memoryRequest: SETTINGS.serverMemory,
        memoryLimit: SETTINGS.serverMemory,
        cpuRequest: "100m",
      },
      jvmFlags: SETTINGS.jvmFlags,
    },
  };
  expectStatus(await groupCreate(request, api), 201, `Creating group ${id}`);
  const version = expectStatus(
    await templateUpload(id, new Blob([TEMPLATE]), api),
    201,
    `Uploading ${id}'s template`,
  );
  // A DYNAMIC group takes a new version only when promoted (D-0042).
  expectStatus(
    await templatePromote(id, { versionId: version.id }, api),
    200,
    `Promoting ${id}'s template`,
  );
  console.log(`Created group ${id}`);
}

async function awaitServers(api: Call): Promise<void> {
  await eventually(
    `${SETTINGS.serversPerGroup} servers running in each load group`,
    async () => {
      for (const id of Object.values(GROUPS)) {
        const servers = expectStatus(
          await serverList({ groupId: id }, api),
          200,
          `Listing ${id}`,
        );
        if (
          servers.filter((server) => server.state === "RUNNING").length <
          SETTINGS.serversPerGroup
        ) {
          return undefined;
        }
      }
      return true;
    },
    READY_TIMEOUT_MS,
  );
  await awaitRunningGroups(api, Object.values(GROUPS));
}

async function runningServers(api: Call): Promise<number> {
  return expectStatus(
    await serverList({}, api),
    200,
    "Listing the servers",
  ).filter((server) => server.state === "RUNNING").length;
}

/** Joins every bot, a wave a second, and returns how many could not join. */
async function joinInWaves(
  swarm: Swarm,
  players: string[],
  latencies: Latencies,
): Promise<number> {
  let failed = 0;
  const waves: Promise<void>[] = [];
  for (let first = 0; first < SETTINGS.players; first += SETTINGS.wave) {
    const count = Math.min(SETTINGS.wave, SETTINGS.players - first);
    waves.push(
      ...Array.from({ length: count }, async (_, offset) => {
        const name = `${NAME_PREFIX}${String(first + offset).padStart(5, "0")}`;
        try {
          latencies.join.push(await swarm.join(name));
          players.push(name);
        } catch (error) {
          failed++;
          console.error(`${name} did not join: ${(error as Error).message}`);
        }
      }),
    );
    await sleep(1000);
  }
  await Promise.all(waves);
  console.log(`${players.length} of ${SETTINGS.players} bots joined`);
  return failed;
}

/**
 * Moves every player on from's servers to the other group, timing each until its bot logs in on the new backend, and
 * returns how many bots did not arrive in time.
 */
async function moveAll(
  api: Call,
  swarm: Swarm,
  players: readonly string[],
  from: string,
  to: string,
  latencies: Latencies,
): Promise<number> {
  const arrivals = new Map<string, number>();
  const started = now();
  const stop = swarm.onLogin((name, at) => {
    if (!arrivals.has(name)) {
      arrivals.set(name, at - started);
    }
  });
  for (const server of expectStatus(
    await serverList({ groupId: from }, api),
    200,
    `Listing ${from}`,
  )) {
    expectStatus(
      await serverTransferAll(server.id, { groupId: to }, api),
      202,
      `Moving ${server.id}'s players`,
    );
  }
  try {
    await eventually(
      `every bot to reach ${to}`,
      () => (arrivals.size === players.length ? true : undefined),
      TRANSFER_TIMEOUT_MS,
    );
  } catch {
    const missing = players.filter((name) => !arrivals.has(name));
    console.error(
      `${missing.length} bots did not reach ${to}, e.g. ${missing.slice(0, 5).join(", ")}`,
    );
  }
  stop();
  latencies.transfer.push(...arrivals.values());
  console.log(
    `Moved ${arrivals.size} of ${players.length} players from ${from} to ${to}`,
  );
  return players.length - arrivals.size;
}

/** How many of the bots the API does not show online in group, after their sessions settle. */
async function lostSessions(
  api: Call,
  players: readonly string[],
  group: string,
): Promise<number> {
  const servers = new Set(
    expectStatus(
      await serverList({ groupId: group }, api),
      200,
      `Listing ${group}`,
    ).map(({ id }) => id),
  );
  const expected = new Set(players);
  let seen = 0;
  try {
    await eventually(
      `every bot's session on ${group}`,
      async () => {
        seen = await onlineIn(api, expected, servers);
        return seen === expected.size ? true : undefined;
      },
      TRANSFER_TIMEOUT_MS,
    );
  } catch {
    console.error(`${expected.size - seen} sessions are missing from ${group}`);
  }
  return expected.size - seen;
}

async function onlineIn(
  api: Call,
  names: ReadonlySet<string>,
  servers: ReadonlySet<string>,
): Promise<number> {
  let found = 0;
  let cursor: string | undefined;
  do {
    const page = expectStatus(
      await playerOnline(
        cursor === undefined ? { limit: 200 } : { cursor, limit: 200 },
        api,
      ),
      200,
      "Listing the online players",
    );
    found += page.items.filter(
      (player) =>
        names.has(player.name) && servers.has(player.session.serverId ?? ""),
    ).length;
    cursor = page.nextCursor ?? undefined;
  } while (cursor !== undefined);
  return found;
}

async function writeReport(
  report: Record<string, unknown> & { passed: boolean },
): Promise<void> {
  await mkdir(REPORT_DIR, { recursive: true });
  const file = joinPath(
    REPORT_DIR,
    `report-${new Date().toISOString().replaceAll(":", "-")}.json`,
  );
  await writeFile(file, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`${report.passed ? "Passed" : "Failed"}; the report is ${file}`);
}

function describe(value: Usage): string {
  return `${Math.round(value.cpu)}m CPU, ${Math.round(value.memory)} MiB`;
}

function number(name: string, fallback: number): number {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number`);
  }
  return value;
}

if (import.meta.main) {
  process.exitCode = (await run(await suite())) ? 0 : 1;
}
