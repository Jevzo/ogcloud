import { createContext, useContext } from "react";
import { readStored, writeStored } from "./stored.ts";

/** The theme follows the system unless the user picked one; theme and the sidebar are remembered on this device. */
export type ThemeChoice = "system" | "light" | "dark";

export interface Preferences {
  theme: ThemeChoice;
  /** The sidebar shows only its icon rail (D-0231). */
  sidebarCollapsed: boolean;
}

const KEY = "ogcloud.preferences";
const DEFAULTS: Preferences = {
  theme: "system",
  sidebarCollapsed: false,
};

export function readPreferences(storage: Storage = localStorage): Preferences {
  const stored = readStored(KEY, storage);
  if (typeof stored !== "object" || stored === null) return DEFAULTS;
  // Only the known fields, so settings that are gone (the density, D-0235) drop out on the next write.
  const {
    theme = DEFAULTS.theme,
    sidebarCollapsed = DEFAULTS.sidebarCollapsed,
  } = stored as Partial<Preferences>;
  return { theme, sidebarCollapsed };
}

export function writePreferences(
  preferences: Preferences,
  storage: Storage = localStorage,
): void {
  writeStored(KEY, preferences, storage);
}

/** The display preferences and how to change them, shared by the shell and the account page. */
export const PreferencesContext = createContext<{
  preferences: Preferences;
  change: (next: Preferences) => void;
}>({ preferences: DEFAULTS, change: () => undefined });

export function usePreferences() {
  return useContext(PreferencesContext);
}

/** The tokens' stylesheet switches on this attribute. */
export function applyPreferences(
  { theme }: Preferences,
  root: HTMLElement = document.documentElement,
): void {
  if (theme === "system") delete root.dataset.theme;
  else root.dataset.theme = theme;
}
