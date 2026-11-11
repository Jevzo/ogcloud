import type { MeView } from "@ogcloud/api-client";
import { createContext, useContext } from "react";

const OWNER_ROLE = "owner";

/** The signed-in user from `/api/v1/me`, provided by the shell before any page renders. */
export const MeContext = createContext<MeView | null>(null);

export function useMe(): MeView {
  const me = useContext(MeContext);
  if (me === null)
    throw new Error("useMe is only available inside the app shell");
  return me;
}

/** Whether the signed-in user holds this API permission (D-0092). */
export function useCan(permission: string): boolean {
  return useMe().permissions.includes(permission);
}

/** Only owners change roles, users' roles and OAuth clients (D-0094, D-0108). */
export function useIsOwner(): boolean {
  return isOwner(useMe());
}

export function isOwner(user: { roleId: string }): boolean {
  return user.roleId === OWNER_ROLE;
}
