/**
 * Where a call goes and as whom: same-origin apps leave origin empty, others name the API's public origin. Callers may
 * bring their own fetch, e.g. one that waits out rate limits. Generated calls pass optional bodies as undefined.
 */
export interface Call extends Omit<RequestInit, "body"> {
  readonly origin?: string;
  readonly token?: string;
  readonly fetch?: (url: string, init: RequestInit) => Promise<Response>;
  readonly body?: BodyInit | null | undefined;
}

const EMPTY_BODIES = new Set([204, 205, 304]);
// How much of a body that is no ProblemDetail an error message quotes.
const QUOTED_CHARACTERS = 200;

/** A ProblemDetail body (RFC 9457), as the API answers its errors. */
export interface Problem {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
}

/** Thrown for every answer outside 2xx, so a value a call returns always means success (D-0189). */
export class ApiError extends Error {
  readonly status: number;
  /** The ProblemDetail the API sent, if it sent one. */
  readonly problem: Problem | undefined;

  constructor(
    method: string,
    path: string,
    status: number,
    problem: Problem | undefined,
    text: string,
  ) {
    const detail =
      problem?.detail ?? problem?.title ?? text.slice(0, QUOTED_CHARACTERS);
    super(`${method} ${path} answered ${status}${detail ? `: ${detail}` : ""}`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }
}

/**
 * Sends one generated call and returns orval's `{ data, status, headers }` shape for its status, or throws an
 * ApiError for an answer outside 2xx.
 */
export async function request<T>(path: string, call: Call): Promise<T> {
  const {
    origin = "",
    token,
    fetch: send = fetch,
    headers,
    body,
    ...init
  } = call;
  const sent = new Headers(headers);
  if (token !== undefined) {
    sent.set("Authorization", `Bearer ${token}`);
  }
  const response = await send(`${origin}${path}`, {
    ...init,
    headers: sent,
    body: body ?? null,
  });
  const text = EMPTY_BODIES.has(response.status) ? "" : await response.text();
  if (!response.ok) {
    throw new ApiError(
      init.method ?? "GET",
      path,
      response.status,
      problemIn(text),
      text,
    );
  }
  const isJson =
    response.headers.get("content-type")?.includes("json") ?? false;
  const data: unknown =
    text === "" ? undefined : isJson ? JSON.parse(text) : text;
  return { data, status: response.status, headers: response.headers } as T;
}

/** The ProblemDetail in an error answer's body, if it holds one. */
export function problemIn(text: string): Problem | undefined {
  try {
    const parsed: unknown = JSON.parse(text);
    return typeof parsed === "object" && parsed !== null
      ? (parsed as Problem)
      : undefined;
  } catch {
    // Not JSON, such as a proxy's error page; the error quotes the text instead.
    return undefined;
  }
}
