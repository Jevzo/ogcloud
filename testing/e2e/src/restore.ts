import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import {
  type Call,
  getTemplateDownloadUrl,
  groupList,
  setupStatus,
  type TemplateVersionView,
  templateList,
} from "@ogcloud/api-client";
import { anonymous, expectStatus } from "./api.ts";
import { signIn } from "./login.ts";
import { awaitRunningGroups } from "./seed.ts";
import { type Suite, suite } from "./suite.ts";

// The restore drill (T-0607): `snapshot` records what a backup must bring back, `verify` checks a restored cluster.

/** Each group's template versions, which must come back with the same ids and contents. */
type Snapshot = Readonly<Record<string, readonly TemplateVersionView[]>>;

const FILE = "restore.json";

async function snapshot({ api, target }: Suite): Promise<void> {
  const taken = await read(api);
  await writeFile(
    join(target.stateDir, FILE),
    `${JSON.stringify(taken, null, 2)}\n`,
    { mode: 0o600 },
  );
  console.log(`Recorded ${Object.keys(taken).length} groups`);
}

async function verify({ api, target, state }: Suite): Promise<void> {
  const expected = JSON.parse(
    await readFile(join(target.stateDir, FILE), "utf8"),
  ) as Snapshot;
  if (
    expectStatus(await setupStatus(anonymous(target)), 200, "Reading setup")
      .pending
  ) {
    throw new Error("The restored cluster asks for a first owner again");
  }
  // The owner's password and authenticator live in PostgreSQL, the machine client too (every call below uses it).
  await signIn(target, state.owner);
  const actual = await read(api);
  const missing = Object.keys(expected).filter((group) => !(group in actual));
  if (missing.length > 0) {
    throw new Error(`Groups missing after the restore: ${missing.join(", ")}`);
  }
  for (const [group, versions] of Object.entries(expected)) {
    const found = JSON.stringify(actual[group]);
    if (found !== JSON.stringify(versions)) {
      throw new Error(
        `${group}'s templates were ${JSON.stringify(versions)}, now ${found}`,
      );
    }
    for (const version of versions) {
      await expectContent(api, group, version);
    }
  }
  await awaitRunningGroups(api, Object.keys(expected));
  console.log("The restore brought back the owner, the groups and templates");
}

async function read(api: Call): Promise<Snapshot> {
  const groups = expectStatus(await groupList(api), 200, "Listing the groups");
  const taken: Record<string, readonly TemplateVersionView[]> = {};
  for (const { id } of groups) {
    taken[id] = expectStatus(
      await templateList(id, api),
      200,
      `Listing the templates of ${id}`,
    );
  }
  return taken;
}

// The generated client reads bodies as text, so the archive is fetched through the call's own fetch.
async function expectContent(
  api: Call,
  group: string,
  version: TemplateVersionView,
): Promise<void> {
  const send = api.fetch ?? fetch;
  const response = await send(
    `${api.origin}${getTemplateDownloadUrl(group, version.id)}`,
    {},
  );
  if (response.status !== 200) {
    throw new Error(
      `Downloading ${group}'s template ${version.id}: the API answered ${response.status}`,
    );
  }
  const sha256 = createHash("sha256")
    .update(new Uint8Array(await response.arrayBuffer()))
    .digest("hex");
  if (sha256 !== version.sha256) {
    throw new Error(
      `${group}'s template ${version.id} has SHA-256 ${sha256}, not ${version.sha256}`,
    );
  }
}

const COMMANDS = { snapshot, verify } as const;

if (import.meta.main) {
  const command = process.argv[2];
  if (command !== "snapshot" && command !== "verify") {
    throw new Error("usage: restore.ts <snapshot|verify>");
  }
  await COMMANDS[command](await suite());
}
