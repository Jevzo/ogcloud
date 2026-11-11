import assert from "node:assert/strict";
import { test } from "node:test";
import { networkGet, networkUpdate } from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { connect, join, LEGACY_VERSION } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import { moveTo, playerName, scenario } from "../suite.ts";
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
  const current = expectStatus(
    await networkGet(context.api),
    200,
    "Reading the network",
  );
  const marker = `E2E maintenance ${playerName("")}`;
  const during = expectStatus(
    await networkUpdate(
      {
        settings: {
          ...current.settings,
          maintenance: true,
          maintenanceKickMessage: marker,
        },
        version: current.version,
      },
      context.api,
    ),
    200,
    "Starting maintenance",
  );
  try {
    const refused = connect(context.target, playerName("maint"));
    players.push(refused);
    assert.match(
      await eventually("the proxy to refuse the player", refused.kicked),
      new RegExp(marker),
    );
  } finally {
    expectStatus(
      await networkUpdate(
        { settings: current.settings, version: during.version },
        context.api,
      ),
      200,
      "Ending maintenance",
    );
  }
  // The load balancer and the proxies hear of the change a moment later.
  const name = playerName("after");
  players.push(
    await eventually("the network to let players in again", () =>
      join(context.target, name),
    ),
  );
});
