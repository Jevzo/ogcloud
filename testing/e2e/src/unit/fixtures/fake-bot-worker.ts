import { type Command, now, type Report } from "../../load/swarm-protocol.ts";

// Stands in for bot-worker.ts: bots named "bad…" fail, every other one joins at once and logs in again right after,
// and "crash" ends the worker.

function report(message: Report): void {
  process.send?.(message);
}

process.on("message", (command: Command) => {
  if (command.type === "quit") {
    process.exit(0);
  }
  if (command.name === "crash") {
    process.exit(3);
  }
  if (command.name.startsWith("bad")) {
    report({
      type: "failed",
      name: command.name,
      reason: `${command.name} could not join: kicked`,
    });
    return;
  }
  report({ type: "joined", name: command.name, ms: 7 });
  report({ type: "login", name: command.name, at: now() });
});
