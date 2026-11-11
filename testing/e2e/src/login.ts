import { createHash, randomBytes } from "node:crypto";
import { setTimeout as sleep } from "node:timers/promises";
import { loginState, mfaSetup } from "@ogcloud/api-client";
import { Browser, type Page } from "./browser.ts";
import { send } from "./http.ts";
import type { Target } from "./target.ts";
import { base32Decode, code, nextStepAt, step } from "./totp.ts";

/** A web user's credentials; owners must hold an authenticator secret before they may use anything (D-0097). */
export interface UserLogin {
  readonly username: string;
  readonly password: string;
  readonly totpSecret?: string;
}

export interface SignedIn {
  readonly accessToken: string;
  /** The authenticator secret, enrolled during this login if the user had to have one and had none. */
  readonly totpSecret?: string;
}

// The dashboard's public PKCE client and its callback (D-0012); the suite stops where the dashboard would take over.
const WEB_CLIENT = "ogcloud-web";
const CALLBACK_PATH = "/auth/callback";
const MFA_PATH = "/login/mfa";

/** Signs in on the API's login pages as the dashboard does, enrolling an authenticator when the user must. */
export async function signIn(
  target: Target,
  user: UserLogin,
  now: () => Date = () => new Date(),
): Promise<SignedIn> {
  const browser = new Browser();
  const callback = `${target.origin}${CALLBACK_PATH}`;
  const verifier = randomBytes(32).toString("base64url");
  const authorize = new URL("/oauth2/authorize", target.origin);
  authorize.search = new URLSearchParams({
    response_type: "code",
    client_id: WEB_CLIENT,
    scope: "openid",
    state: randomBytes(8).toString("hex"),
    redirect_uri: callback,
    code_challenge: createHash("sha256").update(verifier).digest("base64url"),
    code_challenge_method: "S256",
  }).toString();

  // The login pages read their state, CSRF token included, from /login/state (D-0212).
  const state = async () =>
    (await loginState({ origin: target.origin, fetch: browser.fetch })).data;
  page(await browser.open(authorize));
  const { csrf } = await state();
  let reached = await browser.submit(
    new URL("/login", target.origin),
    {
      username: user.username,
      password: user.password,
      [csrf.parameterName]: csrf.token,
    },
    callback,
  );
  let totpSecret = user.totpSecret;
  if (!(reached instanceof URL)) {
    if (reached.url.pathname !== MFA_PATH) {
      throw new Error(
        `Signing in as ${user.username} ended on ${reached.url.href}`,
      );
    }
    const { step } = await state();
    if (step === "ENROLMENT") {
      totpSecret = (
        await mfaSetup({ origin: target.origin, fetch: browser.fetch })
      ).data.secret;
      reached = await answer(
        browser,
        target,
        `${MFA_PATH}/totp-setup`,
        state,
        totpSecret,
        callback,
        now,
      );
    } else if (step === "TOTP") {
      if (totpSecret === undefined) {
        throw new Error(
          `${user.username} has an authenticator, but no secret is known for it`,
        );
      }
      reached = await answer(
        browser,
        target,
        `${MFA_PATH}/totp`,
        state,
        totpSecret,
        callback,
        now,
      );
    } else {
      throw new Error(
        `Signing in as ${user.username} needs the ${step} step, which the suite cannot do`,
      );
    }
  }
  if (!(reached instanceof URL)) {
    throw new Error(
      `Signing in as ${user.username} stopped on ${reached.url.href}`,
    );
  }
  const authorizationCode = reached.searchParams.get("code");
  if (authorizationCode === null) {
    throw new Error(`The callback carries no code: ${reached.href}`);
  }
  const accessToken = await exchange(
    target,
    authorizationCode,
    callback,
    verifier,
  );
  return totpSecret === undefined
    ? { accessToken }
    : { accessToken, totpSecret };
}

// The API refuses a code it has seen before (RFC 6238 §5.2), so a refused code is retried once in the next step.
async function answer(
  browser: Browser,
  target: Target,
  path: string,
  state: () => Promise<{ csrf: { parameterName: string; token: string } }>,
  secret: string,
  callback: string,
  now: () => Date,
): Promise<Page | URL> {
  const key = base32Decode(secret);
  const submitCode = async () => {
    const { csrf } = await state();
    return browser.submit(
      new URL(path, target.origin),
      { code: code(key, step(now())), [csrf.parameterName]: csrf.token },
      callback,
    );
  };
  const first = await submitCode();
  if (first instanceof URL) {
    return first;
  }
  await sleep(nextStepAt(now()).getTime() - now().getTime());
  return submitCode();
}

async function exchange(
  target: Target,
  authorizationCode: string,
  callback: string,
  verifier: string,
): Promise<string> {
  const response = await send(new URL("/oauth2/token", target.origin), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      code: authorizationCode,
      redirect_uri: callback,
      client_id: WEB_CLIENT,
      code_verifier: verifier,
    }),
  });
  return (await accessToken(response)).value;
}

/** A machine client (D-0040) and the scopes it may ask for. */
export interface MachineClient {
  readonly clientId: string;
  readonly clientSecret: string;
  readonly scopes: readonly string[];
}

/** A token with all of the client's scopes; the authorization server grants only the scopes a client asks for. */
export async function clientToken(
  target: Target,
  { clientId, clientSecret, scopes }: MachineClient,
): Promise<Token> {
  const response = await send(new URL("/oauth2/token", target.origin), {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString("base64")}`,
    },
    body: new URLSearchParams({
      grant_type: "client_credentials",
      scope: scopes.join(" "),
    }),
  });
  return accessToken(response);
}

/** An access token and when it expires, in milliseconds since the epoch. */
export interface Token {
  readonly value: string;
  readonly expiresAt: number;
}

async function accessToken(response: Response): Promise<Token> {
  const body: unknown = await response.json();
  if (
    !response.ok ||
    typeof body !== "object" ||
    body === null ||
    !("access_token" in body) ||
    !("expires_in" in body)
  ) {
    throw new Error(
      `The token endpoint answered ${response.status}: ${JSON.stringify(body)}`,
    );
  }
  return {
    value: String(body.access_token),
    expiresAt: Date.now() + Number(body.expires_in) * 1000,
  };
}

function page(reached: Page | URL): Page {
  if (reached instanceof URL) {
    throw new Error(`Expected a page, got a redirect to ${reached.href}`);
  }
  return reached;
}
