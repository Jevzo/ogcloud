import { useOutletContext } from "react-router";

import type { GroupListItem } from "@/types/dashboard";
import type {
    NetworkLockRecord,
    NetworkSettingsRecord,
    NetworkStatusRecord,
    UpdateNetworkPayload,
} from "@/types/network";
import type { RuntimeBundleScope } from "@/types/runtime";

export interface NetworkPageContextValue {
    settings: NetworkSettingsRecord | null;
    status: NetworkStatusRecord;
    groups: GroupListItem[];
    locks: NetworkLockRecord[];
    isLoading: boolean;
    isAdmin: boolean;
    isRestartingNetwork: boolean;
    isTogglingMaintenance: boolean;
    refreshingScope: RuntimeBundleScope | null;
    showErrorMessage: (message: string) => void;
    saveSettings: (
        payload: UpdateNetworkPayload,
        successMessage: string,
    ) => Promise<NetworkSettingsRecord>;
    setMaintenance: (enabled: boolean) => Promise<NetworkSettingsRecord>;
    requestNetworkRestart: () => Promise<void>;
    requestRuntimeRefresh: (scope: RuntimeBundleScope) => Promise<void>;
}

export const useNetworkPageContext = () => useOutletContext<NetworkPageContextValue>();
