import { randomBytes } from "node:crypto";
import { after, before } from "node:test";
import {
  type Call,
  type OnlinePlayerView,
  playerOnline,
  playerTransfer,
  serverGet,
} from "@ogcloud/api-client";
import { asClient, expectStatus } from "./api.ts";
import { leave, type Player } from "./bots.ts";
import { trustInternalCa } from "./cluster.ts";
import { loadState, type SeedState } from "./state.ts";
import { type Target, targetFromEnv } from "./target.ts";
import { eventually } from "./wait.ts";

/** What every scenario starts from: the target, the seed's logins, and the API as the suite's machine client. */
export interface Suite {
  readonly target: Target;
  readonly state: SeedState;
  readonly api: Call;
}

export async function suite(): Promise<Suite> {
  const target = targetFromEnv();
  await trustInternalCa(target);
  const state = await loadState(target);
  if (state?.client === undefined) {
    throw new Error(
      `${target.stateDir} holds no seed; run mise run seed first`,
    );
  }
  return { target, state, api: asClient(target, state.client) };
}

/**
 * Sets up one scenario file: the suite is ready before its tests, and every bot in [players] leaves after them.
 * [context] reads the suite once the tests run.
 */
export function scenario(): {
  readonly context: Suite;
  readonly players: Player[];
} {
  let ready: Suite | undefined;
  const players: Player[] = [];
  before(async () => {
    ready = await suite();
  });
  after(() => leave(...players));
  const current = (): Suite => {
    if (ready === undefined) {
      throw new Error("The scenario's suite is read before its tests run");
    }
    return ready;
  };
  return {
    context: {
      get target() {
        return current().target;
      },
      get state() {
        return current().state;
      },
      get api() {
        return current().api;
      },
    },
    players,
  };
}

/** A fresh, valid Minecraft name, so scenarios never share a player. */
export function playerName(prefix: string): string {
  return `${prefix}${randomBytes(3).toString("hex")}`.slice(0, 16);
}

/** The named player, while the API lists them online. */
export async function onlinePlayer(
  api: Call,
  name: string,
): Promise<OnlinePlayerView | undefined> {
  const online = expectStatus(
    await playerOnline({}, api),
    200,
    "Listing the online players",
  );
  return online.items.find((player) => player.name === name);
}

/** The group of the backend the named player is on, once the API reports one. */
export async function groupOf(
  api: Call,
  name: string,
): Promise<string | undefined> {
  const serverId = (await onlinePlayer(api, name))?.session.serverId;
  if (serverId === undefined || serverId === null) {
    return undefined;
  }
  return expectStatus(
    await serverGet(serverId, api),
    200,
    `Reading server ${serverId}`,
  ).groupId;
}

/** Moves the named player to [groupId] through the API and waits until they are there. */
export async function moveTo(
  api: Call,
  name: string,
  groupId: string,
): Promise<void> {
  const player = await eventually(`${name} to be online`, () =>
    onlinePlayer(api, name),
  );
  expectStatus(
    await playerTransfer(player.uuid, { groupId }, api),
    202,
    `Moving ${name} to ${groupId}`,
  );
  await eventually(`${name} to reach ${groupId}`, async () =>
    (await groupOf(api, name)) === groupId ? groupId : undefined,
  );
}
