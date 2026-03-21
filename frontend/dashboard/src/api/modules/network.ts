import type {
    NetworkGeneralSettings,
    NetworkSettingsRecord,
    ProxyRoutingStrategy,
    UpdateNetworkPayload,
} from "@/types/network";
import {
    networkLocksResponseSchema,
    networkSettingsApiSchema,
    networkStatusSchema,
    type NetworkSettingsApiSchema,
} from "@/features/network/schemas";

import { apiClient, getAuthHeaders, SESSION_EXPIRED_MESSAGE, toApiError } from "./shared";

const DEFAULT_NETWORK_GENERAL_SETTINGS: NetworkGeneralSettings = {
    permissionSystemEnabled: true,
    tablistEnabled: true,
    proxyRoutingStrategy: "LOAD_BASED",
};

const normalizeProxyRoutingStrategy = (
    strategy?: ProxyRoutingStrategy | string | null,
): ProxyRoutingStrategy => {
    if (strategy === "ROUND_ROBIN" || strategy === "LOAD_BASED") {
        return strategy;
    }

    return DEFAULT_NETWORK_GENERAL_SETTINGS.proxyRoutingStrategy;
};

const normalizeNetworkSettings = (payload: NetworkSettingsApiSchema): NetworkSettingsRecord => ({
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
        const response = await apiClient.get<NetworkSettingsApiSchema>("/api/v1/network", {
            headers: getAuthHeaders(accessToken),
        });

        return normalizeNetworkSettings(networkSettingsApiSchema.parse(response.data));
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const getNetworkStatus = async (accessToken: string) => {
    try {
        const response = await apiClient.get("/api/v1/network/status", {
            headers: getAuthHeaders(accessToken),
        });

        return networkStatusSchema.parse(response.data);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const getNetworkLocks = async (accessToken: string) => {
    try {
        const response = await apiClient.get<unknown>("/api/v1/network/locks", {
            headers: getAuthHeaders(accessToken),
        });

        return networkLocksResponseSchema.parse(response.data).locks;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const updateNetworkSettings = async (accessToken: string, payload: UpdateNetworkPayload) => {
    try {
        const response = await apiClient.put<NetworkSettingsApiSchema>("/api/v1/network", payload, {
            headers: getAuthHeaders(accessToken),
        });

        return normalizeNetworkSettings(networkSettingsApiSchema.parse(response.data));
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const toggleNetworkMaintenance = async (accessToken: string, maintenance: boolean) => {
    try {
        const response = await apiClient.put<NetworkSettingsApiSchema>(
            "/api/v1/network/maintenance",
            { maintenance },
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return normalizeNetworkSettings(networkSettingsApiSchema.parse(response.data));
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
