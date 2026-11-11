import assert from "node:assert/strict";
import { test } from "node:test";
import { permissionGroupGet, permissionGroupUpdate } from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { join, LEGACY_VERSION, type Player } from "../bots.ts";
import { GROUPS } from "../seed.ts";
import { HEX_PROTOCOL, LEGACY_PROTOCOL, serverList } from "../status.ts";
import { moveTo, playerName, scenario, withNetworkSettings } from "../suite.ts";
import { eventually } from "../wait.ts";

// In-game texts in the tag format (D-0200): 1.16+ clients see hex colours, older ones the nearest named colours
// (D-0204). These check OgCloud's own legacy output (PandaSpigot's chat, the server list), not Velocity's and
// ViaBackwards' downsampling.
const { context, players } = scenario();
const DEFAULT_GROUP = "default";
const GRADIENT = "<gradient:#ff0000:#0000ff>[E2E]</gradient> ";

/** The chat components [player] receives from now on, as JSON. */
function recordComponents(player: Player): readonly string[] {
  const components: string[] = [];
  player.bot.on("message", (message) => {
    components.push(JSON.stringify(message.json));
  });
  // Since 1.19 a server's chat renderer travels as a player chat's unsigned content, which clients show instead of
  // the plain message; mineflayer reports only the plain one.
  player.bot._client.on(
    "player_chat",
    (packet: { unsignedChatContent?: unknown }) => {
      if (packet.unsignedChatContent !== undefined) {
        components.push(JSON.stringify(packet.unsignedChatContent));
      }
    },
  );
  return components;
}

/** Chats until the player's own line comes back with what [shows] looks for, as a changed rank reaches a moment later. */
function ownLine(
  player: Player,
  marker: string,
  shows: (json: string) => boolean,
): Promise<string> {
  const components = recordComponents(player);
  return eventually(`${player.name} to see its line in the new colours`, () => {
    player.bot.chat(marker);
    return components.find((json) => json.includes(marker) && shows(json));
  });
}

/** The text a status description shows, its components' texts joined. */
function shownText(description: unknown): string {
  const { text = "", extra = [] } = description as {
    text?: string;
    extra?: { text: string }[];
  };
  return text + extra.map((part) => part.text).join("");
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
  const marker = playerName("motd");
  await withNetworkSettings(
    context.api,
    { motd: `<gradient:#ff0000:#0000ff>${marker}</gradient>` },
    "Setting a gradient MOTD",
    async () => {
      // The load balancer serves what the controller rendered into the read model a moment later.
      const modern = await eventually(
        "the 1.20.4 server list to show the new MOTD",
        async () => {
          const { description } = await serverList(
            context.target,
            HEX_PROTOCOL,
          );
          return shownText(description) === marker
            ? JSON.stringify(description)
            : undefined;
        },
      );
      assert.match(modern, /"color":"#ff0000"/);
      // Each load balancer replica refreshes on its own, so the old one may still be answering.
      const legacy = await eventually(
        "the 1.8 server list to show the new MOTD",
        async () => {
          const { description } = await serverList(
            context.target,
            LEGACY_PROTOCOL,
          );
          return shownText(description).replace(/§./g, "") === marker
            ? JSON.stringify(description)
            : undefined;
        },
      );
      assert.match(
        legacy,
        /^\{"text":"§4/,
        "1.8 reads legacy text in the nearest named colours",
      );
    },
  );
});
