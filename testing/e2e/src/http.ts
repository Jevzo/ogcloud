import { setTimeout as sleep } from "node:timers/promises";

// Login and token endpoints allow 10 requests a minute per address (D-0097); the suite waits as a client would.
const ATTEMPTS = 6;
const FALLBACK_WAIT_SECONDS = 10;

/** fetch, retried after the API's Retry-After while it answers 429. */
export async function send(
  url: string | URL,
  init: RequestInit = {},
): Promise<Response> {
  for (let attempt = 1; ; attempt++) {
    const response = await fetch(url, init);
    if (response.status !== 429 || attempt === ATTEMPTS) {
      return response;
    }
    const seconds = Number(
      response.headers.get("retry-after") ?? FALLBACK_WAIT_SECONDS,
    );
    await sleep(
      (Number.isFinite(seconds) ? seconds : FALLBACK_WAIT_SECONDS) * 1000,
    );
  }
}
