import { createContext, useContext } from "react";

/** The signed-in user's API permissions (D-0092), from `/api/v1/me`. */
export const PermissionsContext = createContext<readonly string[]>([]);

export function useCan(permission: string): boolean {
  return useContext(PermissionsContext).includes(permission);
}
