// What the load run's swarm and its bot workers tell each other over IPC.

export type Command =
  | { readonly type: "join"; readonly name: string }
  | { readonly type: "quit" };

export type Report =
  /** The bot stands on a backend, ms after the worker got the command. */
  | { readonly type: "joined"; readonly name: string; readonly ms: number }
  /** Why the bot could not join, naming it. */
  | { readonly type: "failed"; readonly name: string; readonly reason: string }
  /** A joined bot logged in on a backend again, at a time from now(). */
  | { readonly type: "login"; readonly name: string; readonly at: number };

/** Milliseconds on a clock every process on the host shares. */
export function now(): number {
  return performance.timeOrigin + performance.now();
}
