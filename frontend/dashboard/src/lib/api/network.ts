import type {
    NetworkGeneralSettings,
    NetworkSettingsRecord,
    NetworkStatusRecord,
    ProxyRoutingStrategy,
    UpdateNetworkPayload,
} from "@/types/network";

import { apiClient, getAuthHeaders, SESSION_EXPIRED_MESSAGE, toApiError } from "./shared";

const DEFAULT_NETWORK_GENERAL_SETTINGS: NetworkGeneralSettings = {
    permissionSystemEnabled: true,
    tablistEnabled: true,
    proxyRoutingStrategy: "LOAD_BASED",
};

type NetworkSettingsApiRecord = Omit<NetworkSettingsRecord, "general"> & {
    general?: Partial<NetworkGeneralSettings> | null;
};

const normalizeProxyRoutingStrategy = (
    strategy?: ProxyRoutingStrategy | string | null,
): ProxyRoutingStrategy => {
    if (strategy === "ROUND_ROBIN" || strategy === "LOAD_BASED") {
        return strategy;
    }

    return DEFAULT_NETWORK_GENERAL_SETTINGS.proxyRoutingStrategy;
};

const normalizeNetworkSettings = (payload: NetworkSettingsApiRecord): NetworkSettingsRecord => ({
    ...payload,
    general: {
        permissionSystemEnabled:
            payload.general?.permissionSystemEnabled ??
            DEFAULT_NETWORK_GENERAL_SETTINGS.permissionSystemEnabled,
        tablistEnabled:
            payload.general?.tablistEnabled ?? DEFAULT_NETWORK_GENERAL_SETTINGS.tablistEnabled,
        proxyRoutingStrategy: normalizeProxyRoutingStrategy(payload.general?.proxyRoutingStrategy),
    },
});

export const getNetworkSettings = async (accessToken: string) => {
    try {
        const response = await apiClient.get<NetworkSettingsApiRecord>("/api/v1/network", {
            headers: getAuthHeaders(accessToken),
        });

        return normalizeNetworkSettings(response.data);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const getNetworkStatus = async (accessToken: string) => {
    try {
        const response = await apiClient.get<NetworkStatusRecord>("/api/v1/network/status", {
            headers: getAuthHeaders(accessToken),
        });

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const updateNetworkSettings = async (accessToken: string, payload: UpdateNetworkPayload) => {
    try {
        const response = await apiClient.put<NetworkSettingsApiRecord>("/api/v1/network", payload, {
            headers: getAuthHeaders(accessToken),
        });

        return normalizeNetworkSettings(response.data);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const toggleNetworkMaintenance = async (accessToken: string, maintenance: boolean) => {
    try {
        const response = await apiClient.put<NetworkSettingsApiRecord>(
            "/api/v1/network/maintenance",
            { maintenance },
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return normalizeNetworkSettings(response.data);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const requestNetworkRestart = async (accessToken: string) => {
    try {
        await apiClient.post("/api/v1/network/restart", undefined, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
