import { createContext, useContext } from "react";
import { type Call, request } from "../request.ts";

/** How the dashboard reaches the API: its origin, and an access token that is fresh whenever it is read. */
export interface ApiConnection {
  readonly origin?: string;
  readonly accessToken: () => Promise<string>;
}

export const ApiConnectionContext = createContext<ApiConnection | null>(null);

/** orval's mutator for the TanStack Query hooks: every call goes out as the signed-in user. */
export function useApiRequest(): <T>(path: string, call: Call) => Promise<T> {
  const connection = useContext(ApiConnectionContext);
  if (connection === null) {
    throw new Error("The API hooks need an ApiConnectionContext provider");
  }
  return async <T>(path: string, call: Call) =>
    request<T>(path, {
      ...call,
      ...(connection.origin === undefined ? {} : { origin: connection.origin }),
      token: await connection.accessToken(),
    });
}
