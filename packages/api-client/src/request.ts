/**
 * Where a call goes and as whom: same-origin apps leave origin empty, others name the API's public origin. Generated
 * calls pass optional bodies as undefined.
 */
export interface Call extends Omit<RequestInit, "body"> {
  readonly origin?: string;
  readonly token?: string;
  readonly body?: BodyInit | null | undefined;
}

const EMPTY_BODIES = new Set([204, 205, 304]);

/** Sends one generated call and returns orval's `{ data, status, headers }` shape for its status. */
export async function request<T>(path: string, call: Call): Promise<T> {
  const { origin = "", token, headers, body, ...init } = call;
  const sent = new Headers(headers);
  if (token !== undefined) {
    sent.set("Authorization", `Bearer ${token}`);
  }
  const response = await fetch(`${origin}${path}`, {
    ...init,
    headers: sent,
    body: body ?? null,
  });
  const text = EMPTY_BODIES.has(response.status) ? "" : await response.text();
  const isJson =
    response.headers.get("content-type")?.includes("json") ?? false;
  const data: unknown =
    text === "" ? undefined : isJson ? JSON.parse(text) : text;
  return { data, status: response.status, headers: response.headers } as T;
}
