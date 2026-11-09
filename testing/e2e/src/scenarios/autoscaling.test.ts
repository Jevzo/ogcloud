import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { serverList } from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { join, leave, type Player } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import { playerName, type Suite, suite } from "../suite.ts";
import { eventually } from "../wait.ts";

// The lobby holds four players per server and scales up above 80 % (seed.ts); a full lobby gets a second server.
const LOBBY_PLAYERS_PER_SERVER = 4;
let context: Suite;
const players: Player[] = [];

before(async () => {
  context = await suite();
});

after(() => leave(...players));

const runningLobbies = async () =>
  expectStatus(
    await serverList({ groupId: GROUPS.lobby, state: "RUNNING" }, context.api),
    200,
    "Listing the lobby's running servers",
  ).length;

test("a full lobby gets another server, which starts", async () => {
  const before = await runningLobbies();
  for (let index = 0; index < LOBBY_PLAYERS_PER_SERVER * before; index++) {
    players.push(await join(context.target, playerName("fill")));
  }

  // The autoscaler requests it at once; the provisioner and the pod's start take minutes on minikube.
  const after = await eventually(
    "another lobby server to run",
    async () => {
      const count = await runningLobbies();
      return count > before ? count : undefined;
    },
    300_000,
  );
  assert.ok(after > before);
});
