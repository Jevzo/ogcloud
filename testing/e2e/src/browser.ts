import { send } from "./http.ts";

/** A page the browser stopped on: the HTML it shows and where it is. */
export interface Page {
  readonly url: URL;
  readonly html: string;
}

const MAX_REDIRECTS = 20;

/**
 * Just enough of a browser for the API's login pages: one origin's cookies and redirects followed by hand, so the
 * login stops where the app's callback would take over.
 */
export class Browser {
  readonly #cookies = new Map<string, string>();

  /** Follows redirects until a page answers, or returns the redirect that leaves through [stopAt]. */
  async open(
    url: string | URL,
    init: RequestInit = {},
    stopAt?: string,
  ): Promise<Page | URL> {
    let next = new URL(url);
    let request = init;
    for (let hop = 0; hop < MAX_REDIRECTS; hop++) {
      const response = await this.#send(next, request);
      const location = response.headers.get("location");
      if (
        response.status >= 300 &&
        response.status < 400 &&
        location !== null
      ) {
        next = new URL(location, next);
        if (stopAt !== undefined && next.href.startsWith(stopAt)) {
          return next;
        }
        request = {};
        continue;
      }
      const html = await response.text();
      if (!response.ok) {
        throw new Error(
          `${next.href} answered ${response.status}: ${html.slice(0, 200)}`,
        );
      }
      return { url: next, html };
    }
    throw new Error(`More than ${MAX_REDIRECTS} redirects from ${url}`);
  }

  /** Posts a form the way a browser does. */
  submit(
    url: string | URL,
    fields: Record<string, string>,
    stopAt?: string,
  ): Promise<Page | URL> {
    return this.open(
      url,
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams(fields),
      },
      stopAt,
    );
  }

  async #send(url: URL, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers);
    if (this.#cookies.size > 0) {
      headers.set(
        "Cookie",
        [...this.#cookies]
          .map(([name, value]) => `${name}=${value}`)
          .join("; "),
      );
    }
    const response = await send(url, { ...init, headers, redirect: "manual" });
    for (const cookie of response.headers.getSetCookie()) {
      const [pair = ""] = cookie.split(";", 1);
      const separator = pair.indexOf("=");
      const name = pair.slice(0, separator).trim();
      const value = pair.slice(separator + 1).trim();
      if (value === "" || /max-age=0/i.test(cookie)) {
        this.#cookies.delete(name);
      } else {
        this.#cookies.set(name, value);
      }
    }
    return response;
  }
}

/** The value of the named input in [html], e.g. Spring Security's `_csrf`. */
export function inputValue(html: string, name: string): string {
  const input = html.match(new RegExp(`<input[^>]*name="${name}"[^>]*>`))?.[0];
  const value = input?.match(/value="([^"]*)"/)?.[1];
  if (value === undefined) {
    throw new Error(`The page has no ${name} input`);
  }
  return value;
}
