import assert from "node:assert/strict";
import { test } from "node:test";
import { CURRENT_VERSION, join, LEGACY_VERSION } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import { groupOf, playerName, scenario } from "../suite.ts";
import { eventually } from "../wait.ts";

// Players come in through the load balancer and the proxies (D-0118) and land in the network's default group.
const { context, players } = scenario();

test("a current client joins through the load balancer and lands in the lobby", async () => {
  const name = playerName("join");
  players.push(await join(context.target, name, CURRENT_VERSION));

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
