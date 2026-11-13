import { type DensityName, defaultDensity } from "@ogcloud/tokens";
import { createContext, useContext } from "react";
import { readStored, writeStored } from "./stored.ts";

/** The theme follows the system unless the user picked one; theme, density and the sidebar are remembered on this device. */
export type ThemeChoice = "system" | "light" | "dark";

export interface Preferences {
  theme: ThemeChoice;
  density: DensityName;
  /** The sidebar shows only its icon rail (D-0231). */
  sidebarCollapsed: boolean;
}

const KEY = "ogcloud.preferences";
const DEFAULTS: Preferences = {
  theme: "system",
  density: defaultDensity,
  sidebarCollapsed: false,
};

export function readPreferences(storage: Storage = localStorage): Preferences {
  const stored = readStored(KEY, storage);
  return typeof stored === "object" && stored !== null
    ? { ...DEFAULTS, ...(stored as Partial<Preferences>) }
    : DEFAULTS;
}

export function writePreferences(
  preferences: Preferences,
  storage: Storage = localStorage,
): void {
  writeStored(KEY, preferences, storage);
}

/** The display preferences and how to change them, shared by the account menu and the account page. */
export const PreferencesContext = createContext<{
  preferences: Preferences;
  change: (next: Preferences) => void;
}>({ preferences: DEFAULTS, change: () => undefined });

export function usePreferences() {
  return useContext(PreferencesContext);
}

/** The tokens' stylesheet switches on these attributes. */
export function applyPreferences(
  { theme, density }: Preferences,
  root: HTMLElement = document.documentElement,
): void {
  if (theme === "system") delete root.dataset.theme;
  else root.dataset.theme = theme;
  root.dataset.density = density;
}
