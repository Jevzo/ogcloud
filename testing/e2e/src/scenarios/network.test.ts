import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { networkGet, networkUpdate, playerTransfer } from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { connect, join, LEGACY_VERSION, leave, type Player } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import {
  groupOf,
  onlinePlayer,
  playerName,
  type Suite,
  suite,
} from "../suite.ts";
import { eventually } from "../wait.ts";

// Transfers through the API, the 1.8.8 backend behind the same proxies, and network maintenance (v1 parity, D-0004).
let context: Suite;
const players: Player[] = [];

before(async () => {
  context = await suite();
});

after(() => leave(...players));

async function moveTo(name: string, groupId: string): Promise<void> {
  const player = await eventually(`${name} to be online`, () =>
    onlinePlayer(context.api, name),
  );
  expectStatus(
    await playerTransfer(player.uuid, { groupId }, context.api),
    202,
    `Moving ${name} to ${groupId}`,
  );
  assert.equal(
    await eventually(`${name} to reach ${groupId}`, async () => {
      const group = await groupOf(context.api, name);
      return group === groupId ? group : undefined;
    }),
    groupId,
  );
}

test("the API moves a player to the 1.8.8 backend and back to the lobby", async () => {
  const name = playerName("move");
  players.push(await join(context.target, name));

  await moveTo(name, GROUPS.legacy);
  await moveTo(name, GROUPS.lobby);
});

test("a 1.8 client plays on the 1.8.8 backend", async () => {
  const name = playerName("eight");
  players.push(await join(context.target, name, LEGACY_VERSION));

  await moveTo(name, GROUPS.legacy);
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
