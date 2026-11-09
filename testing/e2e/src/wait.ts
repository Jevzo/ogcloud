import { setTimeout as sleep } from "node:timers/promises";

const POLL_MS = 1000;

/** Polls [check] until it returns a value, failing with [what] after [timeoutMs]; the suite runs on real time. */
export async function eventually<T>(
  what: string,
  check: () => Promise<T | undefined> | T | undefined,
  timeoutMs = 120_000,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  let last: unknown;
  while (Date.now() < deadline) {
    try {
      const value = await check();
      if (value !== undefined) {
        return value;
      }
    } catch (error) {
      last = error;
    }
    await sleep(POLL_MS);
  }
  throw new Error(`Timed out after ${timeoutMs} ms waiting for ${what}`, {
    cause: last,
  });
}
