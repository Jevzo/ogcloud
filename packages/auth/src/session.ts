import { createChallenge } from "./pkce.ts";
import type { LoginStore, PendingStore } from "./stores.ts";

/** The OAuth client this app signs in as (D-0012): the dashboard and the desktop app differ only here. */
export interface AuthConfig {
  /** The API's public origin; the dashboard's own, e.g. `https://cloud.ogwars.io`. */
  origin: string;
  clientId: string;
  redirectUri: string;
  /** Where the API's login session sends the browser after signing out; registered for the client. */
  postLogoutRedirectUri: string;
}

export interface AuthDependencies {
  fetch: (url: string, init: RequestInit) => Promise<Response>;
  now: () => number;
  crypto: Crypto;
  logins: LoginStore;
  pending: PendingStore;
  /** Runs one task at a time across every tab, e.g. `navigator.locks.request` (D-0212). */
  exclusively: <T>(task: () => Promise<T>) => Promise<T>;
}

/** No usable login: the app sends the user to sign in. */
export class SignInRequired extends Error {
  constructor(reason: string) {
    super(reason);
    this.name = "SignInRequired";
  }
}

// An access token is renewed when less than this is left, so a request never leaves with one about to expire.
const RENEW_BEFORE_MS = 30_000;
const SCOPE = "openid";

interface TokenResponse {
  access_token: string;
  expires_in: number;
  refresh_token: string;
  id_token?: string;
}

/**
 * One app's login: PKCE sign-in, access tokens kept only in memory, and the rotating refresh token in shared storage,
 * renewed by one tab at a time so no tab presents a token another has already used (D-0090, D-0212).
 */
export class AuthSession {
  private readonly config: AuthConfig;
  private readonly deps: AuthDependencies;
  private access: { token: string; expiresAt: number } | undefined;
  private renewing: Promise<string> | undefined;

  constructor(config: AuthConfig, deps: AuthDependencies) {
    this.config = config;
    this.deps = deps;
  }

  /** The authorize URL to send the browser to; `returnTo` is where the callback lands afterwards. */
  async beginSignIn(returnTo: string): Promise<URL> {
    const { verifier, challenge, state } = await createChallenge(
      this.deps.crypto,
    );
    this.deps.pending.put({ verifier, state, returnTo });
    const url = new URL("/oauth2/authorize", this.config.origin);
    url.search = new URLSearchParams({
      response_type: "code",
      client_id: this.config.clientId,
      redirect_uri: this.config.redirectUri,
      scope: SCOPE,
      state,
      code_challenge: challenge,
      code_challenge_method: "S256",
    }).toString();
    return url;
  }

  /** Finishes the sign-in on the callback URL and returns where to continue. */
  async completeSignIn(callback: URL): Promise<string> {
    const pending = this.deps.pending.take();
    const code = callback.searchParams.get("code");
    if (
      pending === undefined ||
      code === null ||
      callback.searchParams.get("state") !== pending.state
    ) {
      throw new SignInRequired(
        "The sign-in callback does not match a sign-in this tab started",
      );
    }
    const tokens = await this.token({
      grant_type: "authorization_code",
      code,
      redirect_uri: this.config.redirectUri,
      code_verifier: pending.verifier,
    });
    await this.keep(tokens);
    return pending.returnTo;
  }

  /** A valid access token, renewed when it is close to expiring; throws SignInRequired without a login. */
  accessToken(): Promise<string> {
    if (
      this.access !== undefined &&
      this.access.expiresAt - RENEW_BEFORE_MS > this.deps.now()
    ) {
      return Promise.resolve(this.access.token);
    }
    this.renewing ??= this.deps
      .exclusively(async () => {
        const login = await this.deps.logins.read();
        if (login === undefined)
          throw new SignInRequired("Nobody is signed in");
        const tokens = await this.token({
          grant_type: "refresh_token",
          refresh_token: login.refreshToken,
        }).catch(async (error: unknown) => {
          // A refused refresh token is dead for good (expired, revoked or reused); keeping it would only fail again.
          if (error instanceof SignInRequired)
            await this.deps.logins.write(undefined);
          throw error;
        });
        return this.keep(tokens);
      })
      .finally(() => {
        this.renewing = undefined;
      });
    return this.renewing;
  }

  /** Drops the cached access token, so the next call carries fresh permissions, e.g. after a Minecraft link (D-0106). */
  forgetAccessToken(): void {
    this.access = undefined;
  }

  /**
   * Revokes the refresh token, then forgets the login and returns the URL that ends the API's login session. A failed
   * revocation throws with the login kept, so signing out can be tried again.
   */
  async signOut(): Promise<URL> {
    const login = await this.deps.logins.read();
    if (login !== undefined) {
      await this.post("/oauth2/revoke", {
        token: login.refreshToken,
        token_type_hint: "refresh_token",
      });
    }
    this.access = undefined;
    await this.deps.logins.write(undefined);
    const logout = new URL("/oauth2/logout", this.config.origin);
    const query = new URLSearchParams({
      post_logout_redirect_uri: this.config.postLogoutRedirectUri,
    });
    if (login?.idToken !== undefined) query.set("id_token_hint", login.idToken);
    logout.search = query.toString();
    return logout;
  }

  private async keep(tokens: TokenResponse): Promise<string> {
    const stored = await this.deps.logins.read();
    await this.deps.logins.write({
      refreshToken: tokens.refresh_token,
      idToken: tokens.id_token ?? stored?.idToken,
    });
    this.access = {
      token: tokens.access_token,
      expiresAt: this.deps.now() + tokens.expires_in * 1000,
    };
    return tokens.access_token;
  }

  private async token(grant: Record<string, string>): Promise<TokenResponse> {
    const response = await this.post("/oauth2/token", grant);
    const body: unknown = await response.json();
    if (!isTokenResponse(body))
      throw new Error(
        "The token endpoint answered without the tokens a public client gets",
      );
    return body;
  }

  private async post(
    path: string,
    fields: Record<string, string>,
  ): Promise<Response> {
    const response = await this.deps.fetch(
      new URL(path, this.config.origin).toString(),
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          client_id: this.config.clientId,
          ...fields,
        }),
      },
    );
    // OAuth answers a refused grant with 400 and `invalid_grant` (RFC 6749 §5.2): the login is over.
    if (response.status === 400 || response.status === 401) {
      throw new SignInRequired(
        `${path} refused the request with ${response.status}`,
      );
    }
    if (!response.ok) throw new Error(`${path} answered ${response.status}`);
    return response;
  }
}

function isTokenResponse(value: unknown): value is TokenResponse {
  if (typeof value !== "object" || value === null) return false;
  const body = value as Record<string, unknown>;
  return (
    typeof body.access_token === "string" &&
    typeof body.refresh_token === "string" &&
    typeof body.expires_in === "number"
  );
}
