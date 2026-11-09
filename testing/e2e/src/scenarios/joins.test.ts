import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { join, LEGACY_VERSION, leave, type Player } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import { groupOf, playerName, type Suite, suite } from "../suite.ts";
import { eventually } from "../wait.ts";

// Players come in through the load balancer and the proxies (D-0118) and land in the network's default group.
let context: Suite;
const players: Player[] = [];

before(async () => {
  context = await suite();
});

after(() => leave(...players));

test("a current client joins through the load balancer and lands in the lobby", async () => {
  const name = playerName("join");
  players.push(await join(context.target, name));

  assert.equal(
    await eventually(`${name} to be on a backend`, () =>
      groupOf(context.api, name),
    ),
    GROUPS.lobby,
  );
});

test("a 1.8 client joins the 26.x lobby through the Via plugins", async () => {
  const name = playerName("old");
  players.push(await join(context.target, name, LEGACY_VERSION));

  assert.equal(
    await eventually(`${name} to be on a backend`, () =>
      groupOf(context.api, name),
    ),
    GROUPS.lobby,
  );
});
