import { type ChildProcess, fork } from "node:child_process";
import { once } from "node:events";
import type { Command, Report } from "./swarm-protocol.ts";

// The load run's bots, spread over worker processes: one event loop holding hundreds of mineflayer bots fell behind
// their keep-alives, and the proxy timed them out (T-0609).

const WORKER = new URL("./bot-worker.ts", import.meta.url);
// How long a worker may take to quit before it is killed.
const EXIT_TIMEOUT_MS = 10_000;

interface Pending {
  readonly worker: ChildProcess;
  readonly resolve: (ms: number) => void;
  readonly reject: (error: Error) => void;
}

export type LoginListener = (name: string, at: number) => void;

export class Swarm {
  private readonly workers: readonly ChildProcess[];
  private readonly pending = new Map<string, Pending>();
  private readonly listeners = new Set<LoginListener>();
  private turn = 0;

  private constructor(workers: readonly ChildProcess[]) {
    this.workers = workers;
    for (const worker of workers) {
      worker.on("message", (report: Report) => this.received(report));
      worker.once("exit", (code, signal) =>
        this.lost(worker, `its worker exited with ${code ?? signal}`),
      );
    }
  }

  /** Forks count workers; tests pass a worker script of their own. */
  static start(count: number, script: URL = WORKER): Swarm {
    return new Swarm(
      Array.from({ length: count }, () =>
        fork(script, { stdio: ["ignore", "inherit", "inherit", "ipc"] }),
      ),
    );
  }

  /** Joins a bot on the next worker; resolves with the ms it took to stand on a backend, or fails with the reason. */
  join(name: string): Promise<number> {
    const worker = this.workers[this.turn++ % this.workers.length];
    if (worker === undefined || worker.exitCode !== null) {
      return Promise.reject(new Error(`${name} has no worker to join on`));
    }
    return new Promise((resolve, reject) => {
      this.pending.set(name, { worker, resolve, reject });
      worker.send({ type: "join", name } satisfies Command);
    });
  }

  /** Hears every later login of a joined bot, as on a transfer; returns what stops it. */
  onLogin(listener: LoginListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /** Quits every bot and ends the workers. */
  async close(): Promise<void> {
    await Promise.all(
      this.workers.map(async (worker) => {
        if (worker.exitCode !== null || worker.signalCode !== null) {
          return;
        }
        const exited = once(worker, "exit");
        const timer = setTimeout(() => worker.kill(), EXIT_TIMEOUT_MS);
        worker.send({ type: "quit" } satisfies Command);
        await exited;
        clearTimeout(timer);
      }),
    );
  }

  private received(report: Report): void {
    if (report.type === "login") {
      for (const listener of this.listeners) {
        listener(report.name, report.at);
      }
      return;
    }
    const pending = this.pending.get(report.name);
    this.pending.delete(report.name);
    if (report.type === "joined") {
      pending?.resolve(report.ms);
    } else {
      pending?.reject(new Error(report.reason));
    }
  }

  private lost(worker: ChildProcess, reason: string): void {
    for (const [name, pending] of this.pending) {
      if (pending.worker === worker) {
        this.pending.delete(name);
        pending.reject(new Error(`${name} could not join: ${reason}`));
      }
    }
  }
}
