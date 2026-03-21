import { createContext, useContext } from "react";

import type { GroupListItem } from "@/types/dashboard";
import type {
    NetworkLockRecord,
    NetworkSettingsRecord,
    NetworkStatusRecord,
    UpdateNetworkPayload,
} from "@/types/network";

export interface NetworkPageContextValue {
    settings: NetworkSettingsRecord | null;
    status: NetworkStatusRecord;
    groups: GroupListItem[];
    locks: NetworkLockRecord[];
    isLoading: boolean;
    isRefreshing: boolean;
    isAdmin: boolean;
    isRestartingNetwork: boolean;
    isTogglingMaintenance: boolean;
    saveSettings: (
        payload: UpdateNetworkPayload,
        successMessage: string,
    ) => Promise<NetworkSettingsRecord>;
    setMaintenance: (enabled: boolean) => Promise<NetworkSettingsRecord>;
    requestNetworkRestart: () => Promise<void>;
}

const NetworkPageContext = createContext<NetworkPageContextValue | null>(null);

export const NetworkPageContextProvider = NetworkPageContext.Provider;

export const useNetworkPageContext = () => {
    const value = useContext(NetworkPageContext);

    if (!value) {
        throw new Error("useNetworkPageContext must be used within NetworkPageContextProvider.");
    }

    return value;
};
