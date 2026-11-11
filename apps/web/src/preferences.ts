import { type DensityName, defaultDensity } from "@ogcloud/tokens";
import { createContext, useContext } from "react";

/** The theme follows the system unless the user picked one; both are remembered on this device (D-0209). */
export type ThemeChoice = "system" | "light" | "dark";

export interface Preferences {
  theme: ThemeChoice;
  density: DensityName;
}

const KEY = "ogcloud.preferences";
const DEFAULTS: Preferences = { theme: "system", density: defaultDensity };

export function readPreferences(storage: Storage = localStorage): Preferences {
  try {
    const stored: unknown = JSON.parse(storage.getItem(KEY) ?? "null");
    return typeof stored === "object" && stored !== null
      ? { ...DEFAULTS, ...(stored as Partial<Preferences>) }
      : DEFAULTS;
  } catch (error) {
    // Blocked or corrupt storage only costs the user their choice; the defaults still work.
    console.warn("Could not read the display preferences", error);
    return DEFAULTS;
  }
}

export function writePreferences(
  preferences: Preferences,
  storage: Storage = localStorage,
): void {
  try {
    storage.setItem(KEY, JSON.stringify(preferences));
  } catch (error) {
    console.warn("Could not keep the display preferences", error);
  }
}

/** The display preferences and how to change them, shared by the account menu and the account page. */
export const PreferencesContext = createContext<{
  preferences: Preferences;
  change: (next: Preferences) => void;
}>({ preferences: DEFAULTS, change: () => undefined });

export function usePreferences() {
  return useContext(PreferencesContext);
}

/** The tokens' stylesheet switches on these attributes (D-0216). */
export function applyPreferences(
  { theme, density }: Preferences,
  root: HTMLElement = document.documentElement,
): void {
  if (theme === "system") delete root.dataset.theme;
  else root.dataset.theme = theme;
  root.dataset.density = density;
}
