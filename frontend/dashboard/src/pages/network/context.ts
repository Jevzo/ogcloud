import { useOutletContext } from "react-router";

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

export const useNetworkPageContext = () => useOutletContext<NetworkPageContextValue>();
