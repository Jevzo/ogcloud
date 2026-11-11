import { createContext, useContext } from "react";
import { type Call, request } from "../request.ts";

/**
 * How the dashboard reaches the API: its origin, and an access token that is fresh whenever it is read; without one
 * the calls are anonymous, as a fresh install's first-owner setup is.
 */
export interface ApiConnection {
  readonly origin?: string;
  readonly accessToken?: () => Promise<string>;
}

/** Where and as whom a call on this connection goes, as the generated functions take it. */
export async function callOptions(
  connection: ApiConnection,
): Promise<Pick<Call, "origin" | "token">> {
  const token = await connection.accessToken?.();
  return {
    ...(connection.origin === undefined ? {} : { origin: connection.origin }),
    ...(token === undefined ? {} : { token }),
  };
}

export const ApiConnectionContext = createContext<ApiConnection | null>(null);

/** orval's mutator for the TanStack Query hooks: every call goes out as the signed-in user. */
export function useApiRequest(): <T>(path: string, call: Call) => Promise<T> {
  const connection = useContext(ApiConnectionContext);
  if (connection === null) {
    throw new Error("The API hooks need an ApiConnectionContext provider");
  }
  return async <T>(path: string, call: Call) =>
    request<T>(path, { ...call, ...(await callOptions(connection)) });
}
