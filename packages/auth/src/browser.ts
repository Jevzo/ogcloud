import type { AuthDependencies, SignOutChannel } from "./session.ts";
import { indexedDbLoginStore, sessionPendingStore } from "./stores.ts";

const LOCK = "ogcloud-auth-refresh";
const SIGN_OUTS = "ogcloud-auth-sign-out";

/**
 * The browser's own implementations: IndexedDB, session storage, and a Web Lock and a BroadcastChannel shared by the
 * origin's tabs.
 */
export function browserDependencies(): AuthDependencies {
  return {
    fetch: (url, init) => fetch(url, init),
    now: () => Date.now(),
    crypto: globalThis.crypto,
    logins: indexedDbLoginStore(),
    pending: sessionPendingStore(),
    exclusively: (task) => navigator.locks.request(LOCK, task),
    signOuts: broadcastSignOuts(),
  };
}

function broadcastSignOuts(): SignOutChannel {
  const channel = new BroadcastChannel(SIGN_OUTS);
  return {
    announce: () => channel.postMessage(null),
    listen: (listener) => channel.addEventListener("message", listener),
  };
}
