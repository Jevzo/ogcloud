import type {
    NetworkGeneralSettings,
    NetworkLockRecord,
    NetworkLocksResponse,
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

const isRecord = (value: unknown): value is Record<string, unknown> =>
    typeof value === "object" && value !== null && !Array.isArray(value);

const readNullableString = (value: unknown) => {
    if (typeof value === "string") {
        return value;
    }

    return value === null || typeof value === "undefined" ? null : undefined;
};

const readNullableNumber = (value: unknown) => {
    if (typeof value === "number" && Number.isFinite(value)) {
        return value;
    }

    return value === null || typeof value === "undefined" ? null : undefined;
};

const normalizeNetworkLock = (payload: unknown): NetworkLockRecord => {
    if (!isRecord(payload) || typeof payload.key !== "string" || typeof payload.type !== "string") {
        throw new Error("Invalid network locks response.");
    }

    const targetId = readNullableString(payload.targetId);
    const token = readNullableString(payload.token);
    const ttlSeconds = readNullableNumber(payload.ttlSeconds);

    if (
        typeof targetId === "undefined" ||
        typeof token === "undefined" ||
        typeof ttlSeconds === "undefined"
    ) {
        throw new Error("Invalid network locks response.");
    }

    return {
        key: payload.key,
        type: payload.type,
        targetId,
        token,
        ttlSeconds,
    };
};

const normalizeNetworkLocks = (payload: unknown): NetworkLocksResponse => {
    if (!isRecord(payload) || !Array.isArray(payload.locks)) {
        throw new Error("Invalid network locks response.");
    }

    return {
        locks: payload.locks.map(normalizeNetworkLock),
    };
};

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

export const getNetworkLocks = async (accessToken: string) => {
    try {
        const response = await apiClient.get<unknown>("/api/v1/network/locks", {
            headers: getAuthHeaders(accessToken),
        });

        return normalizeNetworkLocks(response.data).locks;
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
