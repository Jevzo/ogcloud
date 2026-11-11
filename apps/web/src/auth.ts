import { ApiError } from "@ogcloud/api-client";
import type { ApiConnection } from "@ogcloud/api-client/react";
import {
  AuthSession,
  browserDependencies,
  SignInRequired,
} from "@ogcloud/auth";
import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";

const WEB_CLIENT = "ogcloud-web";
const CALLBACK_PATH = "/auth/callback";
const STALE_MS = 15_000;
const SERVER_ERROR = 500;
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
    browserDependencies(),
  );
}

export { CALLBACK_PATH };

/** Sends the browser to sign in and back to where it was. */
export async function signIn(session: AuthSession): Promise<void> {
  const here = `${window.location.pathname}${window.location.search}`;
  window.location.assign(
    await session.beginSignIn(here === CALLBACK_PATH ? "/" : here),
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
          if (error instanceof ApiError && error.status === 401) {
            session?.forgetAccessToken();
            return failures < 1;
          }
          if (error instanceof ApiError && error.status < SERVER_ERROR)
            return false;
          return failures < RETRIES;
        },
      },
    },
  });
}
