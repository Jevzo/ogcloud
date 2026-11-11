import assert from "node:assert/strict";
import { test } from "node:test";
import {
  networkGet,
  networkUpdate,
  permissionGroupGet,
  permissionGroupUpdate,
} from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { join, LEGACY_VERSION, type Player } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import { HEX_PROTOCOL, LEGACY_PROTOCOL, serverList } from "../status.ts";
import { moveTo, playerName, scenario } from "../suite.ts";
import { eventually } from "../wait.ts";

// In-game texts in the tag format (D-0200): 1.16+ clients see hex colours, older ones the nearest named colours
// (D-0204), whether OgCloud renders the text (PandaSpigot, the load balancer) or Velocity and ViaBackwards do.
const { context, players } = scenario();
const DEFAULT_GROUP = "default";
const GRADIENT = "<gradient:#ff0000:#0000ff>[E2E]</gradient> ";

/** Chats until the player's own line comes back with what [shows] looks for, as a changed rank reaches a moment later. */
function ownLine(
  player: Player,
  marker: string,
  shows: (json: string) => boolean,
): Promise<string> {
  return eventually(`${player.name} to see its line in the new colours`, () => {
    player.bot.chat(marker);
    return player.components.find(
      (json) => json.includes(marker) && shows(json),
    );
  });
}

test("a gradient prefix reaches 1.16+ clients in hex and 1.8 clients in named colours", async () => {
  const group = expectStatus(
    await permissionGroupGet(DEFAULT_GROUP, context.api),
    200,
    "Reading the default group",
  );
  const changed = expectStatus(
    await permissionGroupUpdate(
      DEFAULT_GROUP,
      {
        settings: {
          ...group.settings,
          display: { ...group.settings.display, chatPrefix: GRADIENT },
        },
        version: group.version,
      },
      context.api,
    ),
    200,
    "Giving the default group a gradient prefix",
  );
  try {
    const modern = await join(context.target, playerName("hex"));
    players.push(modern);
    const legacy = await join(
      context.target,
      playerName("old"),
      LEGACY_VERSION,
    );
    players.push(legacy);
    await moveTo(context.api, legacy.name, GROUPS.legacy);

    await ownLine(modern, `hex ${playerName("")}`, (json) =>
      json.toLowerCase().includes("#ff0000"),
    );
    const old = await ownLine(legacy, `old ${playerName("")}`, (json) =>
      json.includes("dark_red"),
    );
    assert.doesNotMatch(old, /#[0-9a-f]{6}/i, "1.8 shows named colours only");
  } finally {
    expectStatus(
      await permissionGroupUpdate(
        DEFAULT_GROUP,
        { settings: group.settings, version: changed.version },
        context.api,
      ),
      200,
      "Restoring the default group",
    );
  }
});

test("the server list shows the MOTD in hex to 1.16+ clients and in named colours to older ones", async () => {
  const current = expectStatus(
    await networkGet(context.api),
    200,
    "Reading the network",
  );
  const marker = playerName("motd");
  const changed = expectStatus(
    await networkUpdate(
      {
        settings: {
          ...current.settings,
          motd: `<gradient:#ff0000:#0000ff>${marker}</gradient>`,
        },
        version: current.version,
      },
      context.api,
    ),
    200,
    "Setting a gradient MOTD",
  );
  try {
    // The load balancer serves what the controller rendered into the read model a moment later.
    const modern = await eventually(
      "the 1.20.4 server list to show the new MOTD",
      async () => {
        const description = JSON.stringify(
          (await serverList(context.target, HEX_PROTOCOL)).description,
        );
        return description.includes(marker.slice(-1)) &&
          description.includes("#ff0000")
          ? description
          : undefined;
      },
    );
    assert.match(modern, /"extra"/);
    const legacy = JSON.stringify(
      (await serverList(context.target, LEGACY_PROTOCOL)).description,
    );
    assert.match(
      legacy,
      /^\{"text":"§4/,
      "1.8 reads legacy text in the nearest named colours",
    );
  } finally {
    expectStatus(
      await networkUpdate(
        { settings: current.settings, version: changed.version },
        context.api,
      ),
      200,
      "Restoring the MOTD",
    );
  }
});
