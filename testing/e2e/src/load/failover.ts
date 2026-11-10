import { mkdir, writeFile } from "node:fs/promises";
import { join as joinPath } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { fileURLToPath } from "node:url";
import { type Call, serverList } from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { kubectl } from "../cluster.ts";
import { type Suite, suite } from "../suite.ts";
import type { Target } from "../target.ts";
import { eventually } from "../wait.ts";

// The failover checks of T-0609 (D-0175) on a live install with two controllers and Valkey behind Sentinel: the
// controller leader is killed and the takeover timed, then the Valkey primary is killed and every server must still
// run, on the same pod, once the reconciler has had its turns.

const TAKEOVER_LIMIT_MS = 15_000;
const LEASE = "ogcloud-controller";
const SERVERS_NAMESPACE = "ogcloud-servers";
const VALKEY_PODS = "app.kubernetes.io/component=valkey";
const CONTROLLER_PODS = "app.kubernetes.io/component=controller";
// Two reconciler intervals (30 s) and a heartbeat deadline (30 s), so a lost server would have been acted on.
const SETTLE_MS = 90_000;
const FAILOVER_TIMEOUT_MS = 120_000;
const REPORT_DIR = fileURLToPath(
  new URL("../../../../build/load", import.meta.url),
);

interface Lease {
  readonly holder: string;
  readonly renewed: number;
}

async function run(context: Suite): Promise<boolean> {
  const { api, target } = context;
  await expectStandby(target);
  const takeoverMs = await leaderTakeover(target);
  console.log(`A new controller leader took over after ${takeoverMs} ms`);

  const before = await runningServers(api, target);
  const primary = await valkeyPrimary(target);
  const killedAt = Date.now();
  await kill(target, primary, target.namespace);
  const promoted = await eventually(
    "a new Valkey primary",
    async () => {
      const current = await valkeyPrimary(target).catch(() => undefined);
      return current !== undefined && current !== primary ? current : undefined;
    },
    FAILOVER_TIMEOUT_MS,
  );
  const valkeyFailoverMs = Date.now() - killedAt;
  console.log(
    `${promoted} became the Valkey primary ${valkeyFailoverMs} ms after ${primary} was killed`,
  );
  await sleep(SETTLE_MS);
  const after = await runningServers(api, target);
  const lost = [...before].filter(([id, pod]) => after.get(id) !== pod);
  for (const [id] of lost) {
    console.error(`Server ${id} did not survive the Valkey failover`);
  }

  const passed = takeoverMs < TAKEOVER_LIMIT_MS && lost.length === 0;
  await mkdir(REPORT_DIR, { recursive: true });
  const file = joinPath(
    REPORT_DIR,
    `failover-${new Date().toISOString().replaceAll(":", "-")}.json`,
  );
  const report = {
    takeoverMs,
    takeoverLimitMs: TAKEOVER_LIMIT_MS,
    valkeyFailoverMs,
    servers: before.size,
    lost: lost.length,
    passed,
  };
  await writeFile(file, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`${passed ? "Passed" : "Failed"}; the report is ${file}`);
  return passed;
}

/** A takeover needs a second controller that is already running. */
async function expectStandby(target: Target): Promise<void> {
  const ready = (
    await kubectl(target, [
      "get",
      "pods",
      "--selector",
      CONTROLLER_PODS,
      "--output",
      'jsonpath={range .items[*]}{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}',
    ])
  )
    .split("\n")
    .filter((status) => status === "True").length;
  if (ready < 2) {
    throw new Error(
      `${ready} controller replicas are ready; the takeover needs two (controller.replicas: 2)`,
    );
  }
}

/** Kills the leader without letting it hand over, and times until another replica holds the Lease. */
async function leaderTakeover(target: Target): Promise<number> {
  const leader = await lease(target);
  const killedAt = Date.now();
  await kill(target, leader.holder, target.namespace);
  await eventually(
    "another controller to lead",
    async () => {
      const current = await lease(target);
      return current.holder !== leader.holder && current.renewed >= killedAt
        ? true
        : undefined;
    },
    FAILOVER_TIMEOUT_MS,
  );
  return Date.now() - killedAt;
}

async function lease(target: Target): Promise<Lease> {
  const spec = JSON.parse(
    await kubectl(target, ["get", "lease", LEASE, "--output", "json"]),
  ).spec as { holderIdentity?: string; renewTime?: string };
  if (spec.holderIdentity === undefined || spec.renewTime === undefined) {
    throw new Error(`Lease ${LEASE} has no holder`);
  }
  return { holder: spec.holderIdentity, renewed: Date.parse(spec.renewTime) };
}

/** The Valkey pod whose server reports the master role. */
async function valkeyPrimary(target: Target): Promise<string> {
  const pods = (
    await kubectl(target, [
      "get",
      "pods",
      "--selector",
      VALKEY_PODS,
      "--field-selector",
      "status.phase=Running",
      "--output",
      "jsonpath={.items[*].metadata.name}",
    ])
  )
    .split(" ")
    .filter((pod) => pod !== "");
  for (const pod of pods) {
    const role = await kubectl(target, [
      "exec",
      pod,
      "--container",
      "valkey",
      "--",
      "sh",
      "-c",
      ". /scripts/common.sh && cli -p 6379 ROLE",
    ]).catch(() => "");
    if (role.split("\n")[0]?.trim() === "master") {
      return pod;
    }
  }
  throw new Error("no Valkey pod is the primary");
}

/** Every running server with the uid of its pod, which a lost and replaced server would change. */
async function runningServers(
  api: Call,
  target: Target,
): Promise<Map<string, string>> {
  const servers = await eventually("the servers", async () =>
    expectStatus(await serverList({}, api), 200, "Listing the servers"),
  );
  const pods = new Map(
    (
      await kubectl(
        target,
        [
          "get",
          "pods",
          "--output",
          'jsonpath={range .items[*]}{.metadata.labels.cloud\\.ogwars\\.io/server-id}{" "}{.metadata.uid}{"\\n"}{end}',
        ],
        SERVERS_NAMESPACE,
      )
    )
      .split("\n")
      .filter((line) => line !== "")
      .map((line) => {
        const [id = "", uid = ""] = line.split(" ");
        return [plain(id), uid] as const;
      }),
  );
  return new Map(
    servers
      .filter((server) => server.state === "RUNNING")
      .map((server) => [server.id, pods.get(plain(server.id)) ?? "no pod"]),
  );
}

// Pods carry server ids in their 32-character form (D-0064).
function plain(id: string): string {
  return id.replaceAll("-", "");
}

async function kill(
  target: Target,
  pod: string,
  namespace: string,
): Promise<void> {
  await kubectl(
    target,
    ["delete", "pod", pod, "--grace-period=0", "--force", "--wait=false"],
    namespace,
  );
}

if (import.meta.main) {
  process.exitCode = (await run(await suite())) ? 0 : 1;
}
