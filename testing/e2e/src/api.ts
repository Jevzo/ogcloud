import type { Call } from "@ogcloud/api-client";
import { send } from "./http.ts";
import type { Target } from "./target.ts";

/** How the generated client reaches the target as the holder of [token], waiting out rate limits. */
export function as(target: Target, token: string): Call {
  return { origin: target.origin, token, fetch: send };
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
