import { join, leave, type Player } from "../bots.ts";
import { targetFromEnv } from "../target.ts";
import { type Command, now, type Report } from "./swarm-protocol.ts";

// One worker process of the load run's swarm (swarm.ts): it joins the bots it is told to and reports each arrival on
// a backend. The target comes from the same OGCLOUD_E2E_* variables as the parent's.

// Time for the bots' disconnects to leave before the worker exits.
const QUIT_GRACE_MS = 1000;

const send = process.send?.bind(process);
if (send === undefined) {
  throw new Error("bot-worker.ts runs as a process forked by the swarm");
}
const target = targetFromEnv();
const players = new Map<string, Player>();

function report(message: Report): void {
  send?.(message);
}

async function joinBot(name: string): Promise<void> {
  const started = now();
  try {
    const player = await join(target, name);
    players.set(name, player);
    report({ type: "joined", name, ms: now() - started });
    player.bot.on("login", () => report({ type: "login", name, at: now() }));
  } catch (error) {
    report({ type: "failed", name, reason: (error as Error).message });
  }
}

process.on("message", (command: Command) => {
  switch (command.type) {
    case "quit":
      leave(...players.values());
      setTimeout(() => process.exit(0), QUIT_GRACE_MS);
      break;
    case "leave": {
      const player = players.get(command.name);
      players.delete(command.name);
      if (player !== undefined) {
        leave(player);
      }
      break;
    }
    case "join":
      void joinBot(command.name);
      break;
  }
});
