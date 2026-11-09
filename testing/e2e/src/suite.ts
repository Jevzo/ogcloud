import { randomBytes } from "node:crypto";
import {
  type Call,
  type OnlinePlayerView,
  playerOnline,
  serverGet,
} from "@ogcloud/api-client";
import { as, expectStatus } from "./api.ts";
import { trustInternalCa } from "./cluster.ts";
import { clientToken } from "./login.ts";
import { loadState, type SeedState } from "./state.ts";
import { type Target, targetFromEnv } from "./target.ts";

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
  const token = await clientToken(target, state.client);
  return { target, state, api: as(target, token) };
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
