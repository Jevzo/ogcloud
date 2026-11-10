/** CPU in millicores and memory in MiB, as `kubectl top` reports them. */
export interface Usage {
  readonly cpu: number;
  readonly memory: number;
}

/** A Prometheus histogram's cumulative bucket counts by upper bound, summed over its label sets. */
export interface Histogram {
  readonly buckets: ReadonlyMap<number, number>;
  readonly count: number;
}

const QUANTITY = /^(\d+(?:\.\d+)?)(m|Ki|Mi|Gi)?$/;

/** The sum of every pod's usage in `kubectl top pods --no-headers` output. */
export function parseTop(output: string): Usage {
  let cpu = 0;
  let memory = 0;
  for (const line of output.split("\n")) {
    const [, cpuText, memoryText] = line.trim().split(/\s+/);
    if (cpuText === undefined || memoryText === undefined) {
      continue;
    }
    cpu += millicores(cpuText);
    memory += mebibytes(memoryText);
  }
  return { cpu, memory };
}

function millicores(text: string): number {
  const [, value = "", unit] = text.match(QUANTITY) ?? [];
  if (value === "") {
    throw new Error(`${text} is not a CPU quantity`);
  }
  return unit === "m" ? Number(value) : Number(value) * 1000;
}

function mebibytes(text: string): number {
  const [, value = "", unit] = text.match(QUANTITY) ?? [];
  const factor = { Ki: 1 / 1024, Mi: 1, Gi: 1024 }[unit ?? ""];
  if (value === "" || factor === undefined) {
    throw new Error(`${text} is not a memory quantity`);
  }
  return Number(value) * factor;
}

/** The named histogram in Prometheus's text format, its label sets added together. */
export function parseHistogram(text: string, name: string): Histogram {
  const buckets = new Map<number, number>();
  let count = 0;
  for (const line of text.split("\n")) {
    const bucket = line.match(
      new RegExp(`^${name}_bucket\\{.*le="([^"]+)".*\\} (\\S+)$`),
    );
    if (bucket !== null) {
      const bound = bucket[1] === "+Inf" ? Infinity : Number(bucket[1]);
      buckets.set(bound, (buckets.get(bound) ?? 0) + Number(bucket[2]));
      continue;
    }
    const total = line.match(new RegExp(`^${name}_count(?:\\{.*\\})? (\\S+)$`));
    if (total !== null) {
      count += Number(total[1]);
    }
  }
  return { buckets, count };
}

/** The share of the samples taken between before and after that were at most bound, which must be a bucket's. */
export function shareWithin(
  before: Histogram,
  after: Histogram,
  bound: number,
): number {
  const taken = after.count - before.count;
  if (taken <= 0) {
    return Number.NaN;
  }
  const within =
    (after.buckets.get(bound) ?? Number.NaN) - (before.buckets.get(bound) ?? 0);
  return within / taken;
}

/** The p-th percentile (0 to 100) of values, by the nearest rank. */
export function percentile(values: readonly number[], p: number): number {
  if (values.length === 0) {
    return Number.NaN;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const rank = Math.ceil((p / 100) * sorted.length);
  return sorted[Math.min(Math.max(rank, 1), sorted.length) - 1] ?? Number.NaN;
}

/** What the platform used at three points of a run: few servers, all servers without players, and with them. */
export interface Measured {
  readonly baseline: { readonly servers: number; readonly usage: Usage };
  readonly idle: { readonly servers: number; readonly usage: Usage };
  readonly loaded: { readonly players: number; readonly usage: Usage };
}

/** The platform's cost of one more server and one more player, and its usage at the target's size (D-0174). */
export function extrapolate(
  measured: Measured,
  target: { readonly servers: number; readonly players: number },
): {
  readonly perServer: Usage;
  readonly perPlayer: Usage;
  readonly atTarget: Usage;
} {
  const { baseline, idle, loaded } = measured;
  const servers = idle.servers - baseline.servers;
  const perServer = per(difference(idle.usage, baseline.usage), servers);
  const perPlayer = per(difference(loaded.usage, idle.usage), loaded.players);
  const extraServers = target.servers - baseline.servers;
  return {
    perServer,
    perPlayer,
    atTarget: {
      cpu:
        baseline.usage.cpu +
        extraServers * perServer.cpu +
        target.players * perPlayer.cpu,
      memory:
        baseline.usage.memory +
        extraServers * perServer.memory +
        target.players * perPlayer.memory,
    },
  };
}

function difference(a: Usage, b: Usage): Usage {
  return {
    cpu: Math.max(a.cpu - b.cpu, 0),
    memory: Math.max(a.memory - b.memory, 0),
  };
}

function per(usage: Usage, count: number): Usage {
  return count > 0
    ? { cpu: usage.cpu / count, memory: usage.memory / count }
    : { cpu: 0, memory: 0 };
}

const PRE_LOGIN = /^\[(\d\d):(\d\d):(\d\d) INFO\]: UUID of player (\S+) is /;
const PLACED =
  /^\[(\d\d):(\d\d):(\d\d) INFO\]: ([^\s[]+)\[[^\]]*\] logged in with entity id /;
const DAY_SECONDS = 86_400;

/**
 * Seconds from each player's pre-login to their placement in a Paper server's log, in the log's one-second steps:
 * the game server's own share of a join or move (D-0183).
 */
export function paperLogins(log: string): number[] {
  const pending = new Map<string, number[]>();
  const seconds: number[] = [];
  for (const line of log.split("\n")) {
    const pre = line.match(PRE_LOGIN);
    if (pre !== null) {
      const name = pre[4] ?? "";
      pending.set(name, [...(pending.get(name) ?? []), clockSeconds(pre)]);
      continue;
    }
    const placed = line.match(PLACED);
    const name = placed?.[4];
    const started = name === undefined ? undefined : pending.get(name)?.shift();
    if (placed !== null && started !== undefined) {
      seconds.push(
        (clockSeconds(placed) - started + DAY_SECONDS) % DAY_SECONDS,
      );
    }
  }
  return seconds;
}

function clockSeconds(match: RegExpMatchArray): number {
  return Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]);
}
