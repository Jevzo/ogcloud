import type { Call } from "@ogcloud/api-client";
import { send } from "./http.ts";
import { clientToken, type MachineClient, type Token } from "./login.ts";
import type { Target } from "./target.ts";

/** How the generated client reaches the target as the holder of [token], waiting out rate limits. */
export function as(target: Target, token: string): Call {
  return { origin: target.origin, token, fetch: send };
}

// Access tokens live 5 minutes (D-0090); a new one is fetched shortly before the last expires.
const REFRESH_MARGIN_MS = 30_000;

/** Calls as the machine client for as long as a scenario takes. */
export function asClient(target: Target, client: MachineClient): Call {
  let current: Token | undefined;
  return {
    origin: target.origin,
    fetch: async (url, init) => {
      if (
        current === undefined ||
        Date.now() > current.expiresAt - REFRESH_MARGIN_MS
      ) {
        current = await clientToken(target, client);
      }
      const headers = new Headers(init.headers);
      headers.set("Authorization", `Bearer ${current.value}`);
      return send(url, { ...init, headers });
    },
  };
}

/** The same without a token, for the anonymous setup calls. */
export function anonymous(target: Target): Call {
  return { origin: target.origin, fetch: send };
}

/** The body of a generated call's response, failing with the API's answer when the status is not [status]. */
export function expectStatus<
  R extends { status: number; data: unknown },
  S extends R["status"],
>(response: R, status: S, what: string): Extract<R, { status: S }>["data"] {
  if (response.status !== status) {
    throw new Error(
      `${what}: the API answered ${response.status}: ${JSON.stringify(response.data)}`,
    );
  }
  return response.data as Extract<R, { status: S }>["data"];
}
