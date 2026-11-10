import { type Bot, createBot } from "mineflayer";
import type { Target } from "./target.ts";
import { eventually } from "./wait.ts";

/**
 * The versions bots join with, all through the Via plugins on the proxy (D-0079): the newest mineflayer speaks, a
 * steady one for scenarios that stay online (mineflayer's 1.21.2+ bots are kicked for invalid_player_movement within
 * seconds, even standing still), and 1.8.
 */
export const CURRENT_VERSION = "26.1";
export const STEADY_VERSION = "1.20.4";
export const LEGACY_VERSION = "1.8.8";

/** A bot and what happened to it: chat in arrival order, and why it was disconnected. */
export interface Player {
  readonly name: string;
  readonly bot: Bot;
  readonly messages: readonly string[];
  readonly kicked: () => string | undefined;
}

const SPAWN_TIMEOUT_MS = 90_000;

/** Joins through the load balancer in offline mode (D-0162) and resolves once the bot stands on a backend. */
export async function join(
  target: Target,
  name: string,
  version = STEADY_VERSION,
): Promise<Player> {
  const player = connect(target, name, version);
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(
      () =>
        reject(
          new Error(`${name} did not spawn within ${SPAWN_TIMEOUT_MS} ms`),
        ),
      SPAWN_TIMEOUT_MS,
    );
    const fail = (reason: string) => {
      clearTimeout(timer);
      reject(new Error(`${name} could not join: ${reason}`));
    };
    player.bot.once("spawn", () => {
      clearTimeout(timer);
      resolve();
    });
    player.bot.once("kicked", (reason) => fail(`kicked: ${text(reason)}`));
    player.bot.once("end", (reason) => fail(`the connection ended: ${reason}`));
    player.bot.once("error", (error: NodeJS.ErrnoException) =>
      fail(error.message || error.code || "connection error"),
    );
  });
  return player;
}

/** Connects without waiting, for scenarios that expect the network to refuse the player. */
export function connect(
  target: Target,
  name: string,
  version = STEADY_VERSION,
): Player {
  const bot = createBot({
    host: target.minecraftHost,
    port: target.minecraftPort,
    username: name,
    version,
    auth: "offline",
    hideErrors: true,
    // Bots stand still: simulated movement translated across versions gets kicked as invalid_player_movement.
    physicsEnabled: false,
    // Few chunks per bot, so hundreds fit one process in a load run (T-0609).
    viewDistance: "tiny",
  });
  const messages: string[] = [];
  let kickReason: string | undefined;
  bot.on("messagestr", (message) => {
    messages.push(message);
  });
  bot.on("kicked", (reason) => {
    kickReason = text(reason);
  });
  return { name, bot, messages, kicked: () => kickReason };
}

/** The first chat message matching [pattern], once it arrives. */
export function message(
  player: Player,
  pattern: RegExp,
  timeoutMs?: number,
): Promise<RegExpMatchArray> {
  return eventually(
    `${player.name} to read ${pattern}`,
    () =>
      player.messages
        .map((text) => text.match(pattern))
        .find((match) => match !== null) ?? undefined,
    timeoutMs,
  );
}

export function leave(...players: readonly Player[]): void {
  for (const player of players) {
    player.bot.quit();
  }
}

// mineflayer hands over kick reasons as text or as chat components.
function text(reason: unknown): string {
  return typeof reason === "string" ? reason : JSON.stringify(reason);
}
