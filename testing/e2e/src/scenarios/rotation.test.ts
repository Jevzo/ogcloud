import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import {
  jobGet,
  networkRotateForwardingToken,
  serverList,
} from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { join, leave, type Player } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import { groupOf, playerName, type Suite, suite } from "../suite.ts";
import { eventually } from "../wait.ts";

// The forwarding token rotates end to end (D-0078, D-0152): a new token in Infisical, synced into the Secret, the
// proxies rolled onto it, the old one removed. Players still join afterwards.
const ROTATION_TIMEOUT_MS = 900_000;
let context: Suite;
const players: Player[] = [];

before(async () => {
  context = await suite();
});

after(() => leave(...players));

const proxies = async () =>
  expectStatus(
    await serverList({ groupId: GROUPS.proxy }, context.api),
    200,
    "Listing the proxies",
  ).map((server) => server.id);

test("rotating the forwarding token rolls the proxies, and players still join", async () => {
  const before = await proxies();
  const job = expectStatus(
    await networkRotateForwardingToken(context.api),
    202,
    "Starting the rotation",
  );

  const done = await eventually(
    "the rotation job to finish",
    async () => {
      const current = expectStatus(
        await jobGet(job.id, context.api),
        200,
        "Reading the rotation job",
      );
      return current.state === "SUCCEEDED" || current.state === "FAILED"
        ? current
        : undefined;
    },
    ROTATION_TIMEOUT_MS,
  );
  assert.equal(done.state, "SUCCEEDED", done.message);
  const after = await proxies();
  assert.ok(
    before.every((id) => !after.includes(id)),
    `proxies ${before.join(", ")} were not replaced`,
  );

  const name = playerName("rot");
  players.push(await join(context.target, name));
  assert.equal(
    await eventually(`${name} to be on a backend`, () =>
      groupOf(context.api, name),
    ),
    GROUPS.lobby,
  );
});
