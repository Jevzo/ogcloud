import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { test } from "node:test";
import {
  type AuthDependencies,
  AuthSession,
  SignInRequired,
} from "./session.ts";
import type {
  LoginStore,
  PendingSignIn,
  PendingStore,
  StoredLogin,
} from "./stores.ts";

const CONFIG = {
  origin: "https://cloud.example",
  clientId: "ogcloud-web",
  redirectUri: "https://cloud.example/auth/callback",
  postLogoutRedirectUri: "https://cloud.example",
};
const LIFETIME_S = 300;

/** The authorization server as the dashboard sees it: refresh tokens rotate, and a used one is refused (D-0090). */
class FakeServer {
  current = "refresh-0";
  used = new Set<string>();
  requests: Record<string, string>[] = [];
  revoked: string[] = [];
  refuseRevocation = false;
  private issued = 0;

  readonly fetch = async (
    url: string,
    init: RequestInit,
  ): Promise<Response> => {
    const fields = Object.fromEntries(new URLSearchParams(String(init.body)));
    this.requests.push({ path: new URL(url).pathname, ...fields });
    if (url.endsWith("/oauth2/revoke")) {
      if (this.refuseRevocation) return new Response("", { status: 503 });
      this.revoked.push(fields.token ?? "");
      return new Response("", { status: 200 });
    }
    if (fields.grant_type === "refresh_token") {
      if (
        fields.refresh_token !== this.current ||
        this.used.has(fields.refresh_token)
      ) {
        return Response.json({ error: "invalid_grant" }, { status: 400 });
      }
      this.used.add(this.current);
    }
    this.issued += 1;
    this.current = `refresh-${this.issued}`;
    return Response.json({
      access_token: `access-${this.issued}`,
      refresh_token: this.current,
      id_token: `id-${this.issued}`,
      expires_in: LIFETIME_S,
    });
  };

  refreshes(): string[] {
    return this.requests
      .filter((request) => request.grant_type === "refresh_token")
      .map((r) => r.refresh_token ?? "");
  }
}

function memoryLogins(initial?: StoredLogin): LoginStore {
  let stored = initial;
  return {
    read: async () => stored,
    write: async (login) => {
      stored = login;
    },
  };
}

function memoryPending(): PendingStore {
  let pending: PendingSignIn | undefined;
  return {
    take: () => {
      const taken = pending;
      pending = undefined;
      return taken;
    },
    put: (next) => {
      pending = next;
    },
  };
}

/** A lock that runs tasks one after another, as the Web Lock does across tabs. */
function serialLock(): AuthDependencies["exclusively"] {
  let tail: Promise<unknown> = Promise.resolve();
  return (task) => {
    const run = tail.then(task, task);
    tail = run.catch(() => undefined);
    return run;
  };
}

function setup(
  server: FakeServer,
  login?: StoredLogin,
  clock = { now: 0 },
  lock = serialLock(),
) {
  const logins = memoryLogins(login);
  const deps: AuthDependencies = {
    fetch: server.fetch,
    now: () => clock.now,
    crypto: globalThis.crypto,
    logins,
    pending: memoryPending(),
    exclusively: lock,
  };
  return { session: new AuthSession(CONFIG, deps), logins, deps };
}

test("signing in sends an S256 challenge and exchanges the code with its verifier", async () => {
  const server = new FakeServer();
  const { session, logins } = setup(server);

  const authorize = await session.beginSignIn("/servers");
  const query = authorize.searchParams;
  assert.equal(authorize.pathname, "/oauth2/authorize");
  assert.equal(query.get("code_challenge_method"), "S256");
  assert.equal(query.get("client_id"), "ogcloud-web");

  const callback = new URL(
    `${CONFIG.redirectUri}?code=abc&state=${query.get("state")}`,
  );
  assert.equal(await session.completeSignIn(callback), "/servers");

  const exchange = server.requests[0];
  assert.equal(exchange?.code, "abc");
  const challenge = createHash("sha256")
    .update(exchange?.code_verifier ?? "")
    .digest("base64url");
  assert.equal(challenge, query.get("code_challenge"));
  assert.deepEqual(await logins.read(), {
    refreshToken: "refresh-1",
    idToken: "id-1",
  });
  assert.equal(await session.accessToken(), "access-1");
});

test("a callback whose state this tab did not send is refused", async () => {
  const { session } = setup(new FakeServer());
  await session.beginSignIn("/");
  await assert.rejects(
    session.completeSignIn(
      new URL(`${CONFIG.redirectUri}?code=abc&state=forged`),
    ),
    SignInRequired,
  );
});

test("the access token is reused until 30 seconds before it expires, then renewed with the stored refresh token", async () => {
  const server = new FakeServer();
  const clock = { now: 0 };
  const { session, logins } = setup(
    server,
    { refreshToken: "refresh-0", idToken: "id-0" },
    clock,
  );

  assert.equal(await session.accessToken(), "access-1");
  clock.now = (LIFETIME_S - 31) * 1000;
  assert.equal(await session.accessToken(), "access-1");
  clock.now = (LIFETIME_S - 29) * 1000;
  assert.equal(await session.accessToken(), "access-2");

  assert.deepEqual(server.refreshes(), ["refresh-0", "refresh-1"]);
  assert.equal((await logins.read())?.refreshToken, "refresh-2");
});

test("calls that need a token at once share one renewal", async () => {
  const server = new FakeServer();
  const { session } = setup(server, {
    refreshToken: "refresh-0",
    idToken: undefined,
  });

  const tokens = await Promise.all([
    session.accessToken(),
    session.accessToken(),
    session.accessToken(),
  ]);

  assert.deepEqual(tokens, ["access-1", "access-1", "access-1"]);
  assert.deepEqual(server.refreshes(), ["refresh-0"]);
});

test("two tabs renewing together never present the same refresh token", async () => {
  const server = new FakeServer();
  const logins = memoryLogins({
    refreshToken: "refresh-0",
    idToken: undefined,
  });
  const lock = serialLock();
  const tab = () =>
    new AuthSession(CONFIG, {
      fetch: server.fetch,
      now: () => 0,
      crypto: globalThis.crypto,
      logins,
      pending: memoryPending(),
      exclusively: lock,
    });

  const [first, second] = await Promise.all([
    tab().accessToken(),
    tab().accessToken(),
  ]);

  assert.notEqual(first, second);
  assert.deepEqual(server.refreshes(), ["refresh-0", "refresh-1"]);
});

test("a refused refresh token is forgotten and asks for a new sign-in", async () => {
  const server = new FakeServer();
  server.current = "rotated-elsewhere";
  const { session, logins } = setup(server, {
    refreshToken: "refresh-0",
    idToken: undefined,
  });

  await assert.rejects(session.accessToken(), SignInRequired);
  assert.equal(await logins.read(), undefined);
  await assert.rejects(session.accessToken(), SignInRequired);
});

test("signing out revokes the refresh token, forgets the login and ends the API's login session", async () => {
  const server = new FakeServer();
  const { session, logins } = setup(server, {
    refreshToken: "refresh-0",
    idToken: "id-0",
  });

  const logout = await session.signOut();

  assert.deepEqual(server.revoked, ["refresh-0"]);
  assert.equal(await logins.read(), undefined);
  assert.equal(logout.pathname, "/oauth2/logout");
  assert.equal(logout.searchParams.get("id_token_hint"), "id-0");
  assert.equal(
    logout.searchParams.get("post_logout_redirect_uri"),
    CONFIG.postLogoutRedirectUri,
  );
});

test("a failed revocation keeps the login, so signing out can be tried again", async () => {
  const server = new FakeServer();
  server.refuseRevocation = true;
  const { session, logins } = setup(server, {
    refreshToken: "refresh-0",
    idToken: "id-0",
  });

  await assert.rejects(session.signOut());
  assert.equal((await logins.read())?.refreshToken, "refresh-0");
});
