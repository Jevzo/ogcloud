import type { AuthSession } from "@ogcloud/auth";
import { createContext, useContext } from "react";

/** The signed-in session, or none in mocks mode, where nothing signs in. */
export const SessionContext = createContext<AuthSession | null>(null);

export function useSession(): AuthSession | null {
  return useContext(SessionContext);
}
