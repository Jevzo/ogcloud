import type { AuthDependencies } from "./session.ts";
import { indexedDbLoginStore, sessionPendingStore } from "./stores.ts";

const LOCK = "ogcloud-auth-refresh";

/** The browser's own implementations: IndexedDB, session storage and a Web Lock shared by the origin's tabs. */
export function browserDependencies(): AuthDependencies {
  return {
    fetch: (url, init) => fetch(url, init),
    now: () => Date.now(),
    crypto: globalThis.crypto,
    logins: indexedDbLoginStore(),
    pending: sessionPendingStore(),
    exclusively: (task) => navigator.locks.request(LOCK, task),
  };
}
