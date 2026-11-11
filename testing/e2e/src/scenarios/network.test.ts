import assert from "node:assert/strict";
import { test } from "node:test";
import { connect, join, LEGACY_VERSION } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import { moveTo, playerName, scenario, withNetworkSettings } from "../suite.ts";
import { eventually } from "../wait.ts";

// Transfers through the API, the 1.8.8 backend behind the same proxies, and network maintenance (v1 parity, D-0004).
const { context, players } = scenario();

test("the API moves a player to the 1.8.8 backend and back to the lobby", async () => {
  const name = playerName("move");
  players.push(await join(context.target, name));

  await moveTo(context.api, name, GROUPS.legacy);
  await moveTo(context.api, name, GROUPS.lobby);
});

test("a 1.8 client plays on the 1.8.8 backend", async () => {
  const name = playerName("eight");
  players.push(await join(context.target, name, LEGACY_VERSION));

  await moveTo(context.api, name, GROUPS.legacy);
});

test("network maintenance turns new players away until it ends", async () => {
  const marker = `E2E maintenance ${playerName("")}`;
  await withNetworkSettings(
    context.api,
    { maintenance: true, maintenanceKickMessage: marker },
    "Starting maintenance",
    async () => {
      const refused = connect(context.target, playerName("maint"));
      players.push(refused);
      assert.match(
        await eventually("the proxy to refuse the player", refused.kicked),
        new RegExp(marker),
      );
    },
  );
  // The load balancer and the proxies hear of the change a moment later.
  const name = playerName("after");
  players.push(
    await eventually("the network to let players in again", () =>
      join(context.target, name),
    ),
  );
});
