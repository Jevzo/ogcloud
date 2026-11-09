import { createHash, randomBytes } from "node:crypto";
import { setTimeout as sleep } from "node:timers/promises";
import { Browser, inputValue, type Page } from "./browser.ts";
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
  /** The authenticator secret, enrolled during this login if the user had none. */
  readonly totpSecret: string;
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

  const loginPage = page(await browser.open(authorize));
  let reached = await browser.submit(
    new URL("/login", target.origin),
    {
      username: user.username,
      password: user.password,
      _csrf: inputValue(loginPage.html, "_csrf"),
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
    if (reached.html.includes(`${MFA_PATH}/totp-setup`)) {
      const setup = page(
        await browser.open(new URL(`${MFA_PATH}/totp-setup`, target.origin)),
      );
      totpSecret = setup.html.match(/<code>([A-Z2-7=]+)<\/code>/)?.[1];
      if (totpSecret === undefined) {
        throw new Error("The setup page shows no secret");
      }
      reached = await answer(
        browser,
        target,
        `${MFA_PATH}/totp-setup`,
        setup,
        totpSecret,
        callback,
        now,
      );
    } else {
      if (totpSecret === undefined) {
        throw new Error(
          `${user.username} has an authenticator, but no secret is known for it`,
        );
      }
      reached = await answer(
        browser,
        target,
        `${MFA_PATH}/totp`,
        reached,
        totpSecret,
        callback,
        now,
      );
    }
  }
  if (!(reached instanceof URL) || totpSecret === undefined) {
    throw new Error(
      `Signing in as ${user.username} never reached the callback`,
    );
  }
  const authorizationCode = reached.searchParams.get("code");
  if (authorizationCode === null) {
    throw new Error(`The callback carries no code: ${reached.href}`);
  }
  return {
    accessToken: await exchange(target, authorizationCode, callback, verifier),
    totpSecret,
  };
}

// The API refuses a code it has seen before (RFC 6238 §5.2), so a refused code is retried once in the next step.
async function answer(
  browser: Browser,
  target: Target,
  path: string,
  form: Page,
  secret: string,
  callback: string,
  now: () => Date,
): Promise<Page | URL> {
  const key = base32Decode(secret);
  const submitCode = (csrf: string) =>
    browser.submit(
      new URL(path, target.origin),
      { code: code(key, step(now())), _csrf: csrf },
      callback,
    );
  const first = await submitCode(inputValue(form.html, "_csrf"));
  if (first instanceof URL) {
    return first;
  }
  await sleep(nextStepAt(now()).getTime() - now().getTime());
  return submitCode(inputValue(first.html, "_csrf"));
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
  return accessToken(response);
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
): Promise<string> {
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

async function accessToken(response: Response): Promise<string> {
  const body: unknown = await response.json();
  if (
    !response.ok ||
    typeof body !== "object" ||
    body === null ||
    !("access_token" in body)
  ) {
    throw new Error(
      `The token endpoint answered ${response.status}: ${JSON.stringify(body)}`,
    );
  }
  return String(body.access_token);
}

function page(reached: Page | URL): Page {
  if (reached instanceof URL) {
    throw new Error(`Expected a page, got a redirect to ${reached.href}`);
  }
  return reached;
}
