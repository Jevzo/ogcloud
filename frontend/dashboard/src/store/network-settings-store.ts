import { create } from "zustand";

import type { NetworkGeneralSettings } from "@/types/network";

const DEFAULT_GENERAL_SETTINGS: NetworkGeneralSettings = {
  permissionSystemEnabled: true,
  tablistEnabled: true,
};

interface NetworkSettingsState {
  general: NetworkGeneralSettings;
  setGeneral: (general?: Partial<NetworkGeneralSettings> | null) => void;
}

const sanitizeGeneralSettings = (
  general?: Partial<NetworkGeneralSettings> | null
): NetworkGeneralSettings => ({
  permissionSystemEnabled:
    general?.permissionSystemEnabled ?? DEFAULT_GENERAL_SETTINGS.permissionSystemEnabled,
  tablistEnabled: general?.tablistEnabled ?? DEFAULT_GENERAL_SETTINGS.tablistEnabled,
});

export const useNetworkSettingsStore = create<NetworkSettingsState>((set) => ({
  general: DEFAULT_GENERAL_SETTINGS,
  setGeneral: (general) => set({ general: sanitizeGeneralSettings(general) }),
}));
