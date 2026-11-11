import type { AuthSession } from "@ogcloud/auth";
import { useToast } from "@ogcloud/ui";
import { createContext, useCallback, useContext } from "react";
import { useTranslation } from "react-i18next";

/** The signed-in session, or none in mocks mode, where nothing signs in. */
export const SessionContext = createContext<AuthSession | null>(null);

export function useSession(): AuthSession | null {
  return useContext(SessionContext);
}

/** Ends this device's login and leaves through the API's logout; says so when that fails. */
export function useSignOut(): () => Promise<void> {
  const { t } = useTranslation("web");
  const session = useSession();
  const toast = useToast();
  return useCallback(async () => {
    if (session === null) return;
    try {
      window.location.assign(await session.signOut());
    } catch (error) {
      console.warn("Signing out failed", error);
      toast({ message: t("shell.signOutFailed") });
    }
  }, [session, t, toast]);
}
