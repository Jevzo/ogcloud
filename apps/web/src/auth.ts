import { loginState, setupStatus } from "@ogcloud/api-client";
import type { ApiConnection } from "@ogcloud/api-client/react";
import {
  AuthSession,
  browserDependencies,
  SignInRequired,
} from "@ogcloud/auth";
import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { HttpStatus, statusOf } from "./http.ts";

/** The dashboard's OAuth client (D-0012). */
export const WEB_CLIENT = "ogcloud-web";
const CALLBACK_PATH = "/auth/callback";
// The login chain's logout, posted with the login pages' CSRF token (D-0227).
const LOGOUT_PATH = "/login/logout";
const STALE_MS = 15_000;
const RETRIES = 2;

/** The dashboard's login with the API's authorization server, on this same origin (D-0098, D-0212). */
export function createSession(
  origin: string = window.location.origin,
): AuthSession {
  return new AuthSession(
    {
      origin,
      clientId: WEB_CLIENT,
      redirectUri: `${origin}${CALLBACK_PATH}`,
      postLogoutRedirectUri: origin,
    },
    {
      ...browserDependencies(),
      endLoginSession: () => endLoginSession(origin),
    },
  );
}

/** Ends the API's login session in this browser, so signing out needs no ID token (D-0227). */
async function endLoginSession(origin: string): Promise<void> {
  const { csrf } = (await loginState({ origin })).data;
  const response = await fetch(`${origin}${LOGOUT_PATH}`, {
    method: "POST",
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!response.ok) {
    throw new Error(`${LOGOUT_PATH} answered ${response.status}`);
  }
}

/** A fresh install's setup (T-0710), open before anyone can sign in. */
const SETUP_PATH = "/setup";

export { CALLBACK_PATH, SETUP_PATH };

/**
 * Before the first owner exists nobody can sign in, so a fresh install's visitor sets it up instead (D-0094);
 * otherwise the browser signs in and comes back to where it was.
 */
export async function signInOrSetUp(
  session: AuthSession,
  toSetup: () => void,
): Promise<void> {
  const pending = await setupStatus().then(
    (status) => status.data.pending,
    (error: unknown) => {
      // Without an answer the sign-in page is the better guess: it explains an API that is down.
      console.error("Could not tell whether this install is set up", error);
      return false;
    },
  );
  if (pending) toSetup();
  else await signIn(session);
}

/** Sends the browser to sign in and back to where it was; a failed callback goes home afterwards, not to itself. */
export async function signIn(session: AuthSession): Promise<void> {
  const { pathname, search } = window.location;
  window.location.assign(
    await session.beginSignIn(
      pathname === CALLBACK_PATH ? "/" : `${pathname}${search}`,
    ),
  );
}

export function connectionFor(session: AuthSession): ApiConnection {
  return { accessToken: () => session.accessToken() };
}

/**
 * Queries retry only what can recover: a token the API turned down is dropped and fetched again once, client errors
 * are not repeated, and a missing login sends the browser to sign in.
 */
export function createQueryClient(
  session: AuthSession | null,
  onSignInRequired: () => void,
): QueryClient {
  const onError = (error: Error) => {
    if (error instanceof SignInRequired) onSignInRequired();
  };
  return new QueryClient({
    queryCache: new QueryCache({ onError }),
    mutationCache: new MutationCache({ onError }),
    defaultOptions: {
      queries: {
        staleTime: STALE_MS,
        retry: (failures, error) => {
          if (error instanceof SignInRequired) return false;
          const status = statusOf(error);
          if (status === HttpStatus.UNAUTHORIZED) {
            session?.forgetAccessToken();
            return failures < 1;
          }
          if (status !== undefined && status < HttpStatus.SERVER_ERROR)
            return false;
          return failures < RETRIES;
        },
      },
    },
  });
}
