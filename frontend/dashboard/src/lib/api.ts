import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { recordApiLatencySample } from "@/lib/api-latency";

import type { ApiErrorResponse, AuthSession, AuthUser, LoginCredentials } from "@/types/auth";
import type { ApiAuditLogRecord } from "@/types/audit";
import type {
    DashboardOverviewResponse,
    DashboardOverviewScalingAction,
    GroupListItem,
    PaginatedResponse,
} from "@/types/dashboard";
import { getPaginatedHasNext } from "@/types/dashboard";
import type { CreateGroupPayload, GroupRecord, UpdateGroupPayload } from "@/types/group";
import type { ExecuteCommandPayload } from "@/types/command";
import type {
    NetworkGeneralSettings,
    NetworkSettingsRecord,
    NetworkStatusRecord,
    ProxyRoutingStrategy,
    UpdateNetworkPayload,
} from "@/types/network";
import type {
    CreatePermissionGroupPayload,
    PermissionGroupRecord,
    UpdatePermissionGroupPayload,
} from "@/types/permission";
import type { PersistedPlayerRecord, PlayerRecord } from "@/types/player";
import type { SearchResponse } from "@/types/search";
import type { TemplateRecord } from "@/types/template";
import type { OnlinePlayerRecord, ServerRecord } from "@/types/server";
import type { CreateWebUserPayload, UpdateWebUserPayload, WebUserRecord } from "@/types/web-user";

type RuntimeConfigHost = {
    __OGCLOUD_CONFIG__?: {
        apiBaseUrl?: string;
    };
};

const DEFAULT_ERROR_MESSAGE = "Request failed.";
const DEFAULT_LOGIN_UNAUTHORIZED_MESSAGE = "Invalid email or password.";
const DEFAULT_UNAUTHORIZED_MESSAGE = "Unauthorized request.";
const SESSION_EXPIRED_MESSAGE = "Your session expired. Please sign in again.";
const MAX_LIST_PAGE_COUNT = 1_000;

const getApiBaseUrl = () => {
    const runtimeConfig = (globalThis as RuntimeConfigHost).__OGCLOUD_CONFIG__;
    return (runtimeConfig?.apiBaseUrl ?? import.meta.env.VITE_API_BASE_URL ?? "").replace(
        /\/+$/,
        "",
    );
};

const API_BASE_URL = getApiBaseUrl();
const MAX_LIST_PAGE_SIZE = 200;
const DEFAULT_NETWORK_GENERAL_SETTINGS: NetworkGeneralSettings = {
    permissionSystemEnabled: true,
    tablistEnabled: true,
    proxyRoutingStrategy: "LOAD_BASED",
};

const apiClient = axios.create({
    baseURL: API_BASE_URL || undefined,
    headers: {
        "Content-Type": "application/json",
    },
});

type TimedRequestConfig = InternalAxiosRequestConfig & {
    metadata?: {
        startedAtMs: number;
    };
};

const getNowMs = () =>
    typeof performance !== "undefined" && typeof performance.now === "function"
        ? performance.now()
        : Date.now();

const recordRequestLatency = (config?: TimedRequestConfig) => {
    const startedAtMs = config?.metadata?.startedAtMs;

    if (typeof startedAtMs !== "number" || !Number.isFinite(startedAtMs)) {
        return;
    }

    const latencyMs = Math.max(1, Math.round(getNowMs() - startedAtMs));
    recordApiLatencySample(latencyMs);
};

apiClient.interceptors.request.use((config) => {
    const nextConfig = config as TimedRequestConfig;
    nextConfig.metadata = {
        startedAtMs: getNowMs(),
    };
    return nextConfig;
});

apiClient.interceptors.response.use(
    (response) => {
        recordRequestLatency(response.config as TimedRequestConfig);
        return response;
    },
    (error) => {
        if (axios.isAxiosError(error)) {
            recordRequestLatency(error.config as TimedRequestConfig | undefined);
        }

        return Promise.reject(error);
    },
);

class ApiError extends Error {
    status: number;
    details: readonly string[];

    constructor(payload: Partial<ApiErrorResponse>, fallbackStatus: number) {
        super(payload.message || DEFAULT_ERROR_MESSAGE);
        this.name = "ApiError";
        this.status = payload.status ?? fallbackStatus;
        this.details = payload.details ?? [];
    }
}

const toApiError = (error: unknown, unauthorizedMessage?: string) => {
    if (axios.isAxiosError(error)) {
        const axiosError = error as AxiosError<Partial<ApiErrorResponse>>;
        const status = axiosError.response?.status ?? 0;
        const payload = axiosError.response?.data;
        const fallbackMessage =
            status === 401
                ? unauthorizedMessage || DEFAULT_UNAUTHORIZED_MESSAGE
                : axiosError.message || DEFAULT_ERROR_MESSAGE;

        return new ApiError(
            {
                status,
                message: payload?.message || fallbackMessage,
                details: payload?.details ?? [],
            },
            status,
        );
    }

    if (error instanceof Error && error.message) {
        return new ApiError({ message: error.message, details: [] }, 0);
    }

    return new ApiError({ message: DEFAULT_ERROR_MESSAGE, details: [] }, 0);
};

const getAuthHeaders = (accessToken: string) => ({
    Authorization: `Bearer ${accessToken}`,
});

type DashboardOverviewApiResponse = Omit<DashboardOverviewResponse, "scalingActions"> & {
    scalingActions?: DashboardOverviewScalingAction[] | null;
};

const normalizeDashboardOverview = (
    payload: DashboardOverviewApiResponse,
): DashboardOverviewResponse => ({
    ...payload,
    scalingActions: Array.isArray(payload.scalingActions) ? payload.scalingActions : [],
});

type NetworkSettingsApiRecord = Omit<NetworkSettingsRecord, "general"> & {
    general?: Partial<NetworkGeneralSettings> | null;
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

const normalizeProxyRoutingStrategy = (
    strategy?: ProxyRoutingStrategy | string | null,
): ProxyRoutingStrategy => {
    if (strategy === "ROUND_ROBIN" || strategy === "LOAD_BASED") {
        return strategy;
    }

    return DEFAULT_NETWORK_GENERAL_SETTINGS.proxyRoutingStrategy;
};

export const loginWithEmailPassword = async (credentials: LoginCredentials) => {
    try {
        const response = await apiClient.post<AuthSession>("/api/v1/auth/login", credentials);
        return response.data;
    } catch (error) {
        throw toApiError(error, DEFAULT_LOGIN_UNAUTHORIZED_MESSAGE);
    }
};

export const refreshSessionToken = async (refreshToken: string) => {
    try {
        const response = await apiClient.post<AuthSession>("/api/v1/auth/refresh", {
            refreshToken,
        });
        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const updateOwnProfile = async (
    accessToken: string,
    updates: { email?: string; password?: string },
) => {
    try {
        const response = await apiClient.put<AuthUser>("/api/v1/auth/me", updates, {
            headers: getAuthHeaders(accessToken),
        });
        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const requestMinecraftLinkOtp = async (accessToken: string, minecraftUsername: string) => {
    try {
        await apiClient.post(
            "/api/v1/auth/link/request",
            { minecraftUsername },
            { headers: getAuthHeaders(accessToken) },
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const confirmMinecraftLinkOtp = async (accessToken: string, otp: string) => {
    try {
        const response = await apiClient.post<AuthUser>(
            "/api/v1/auth/link/confirm",
            { otp },
            { headers: getAuthHeaders(accessToken) },
        );
        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const getDashboardOverview = async (accessToken: string) => {
    const startedAt = performance.now();

    try {
        const response = await apiClient.get<DashboardOverviewApiResponse>(
            "/api/v1/dashboard/overview",
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return {
            data: normalizeDashboardOverview(response.data),
            latencyMs: Math.max(1, Math.round(performance.now() - startedAt)),
        };
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
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

export const listApiAuditLogs = async (
    accessToken: string,
    params?: {
        query?: string;
        page?: number;
        size?: number;
    },
) => {
    try {
        const response = await apiClient.get<PaginatedResponse<ApiAuditLogRecord>>(
            "/api/v1/audit/api",
            {
                headers: getAuthHeaders(accessToken),
                params,
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listWebUsers = async (
    accessToken: string,
    params?: {
        query?: string;
        page?: number;
        size?: number;
    },
) => {
    try {
        const response = await apiClient.get<PaginatedResponse<WebUserRecord>>(
            "/api/v1/web/users",
            {
                headers: getAuthHeaders(accessToken),
                params,
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const createWebUser = async (accessToken: string, payload: CreateWebUserPayload) => {
    try {
        const response = await apiClient.post<WebUserRecord>("/api/v1/web/users", payload, {
            headers: getAuthHeaders(accessToken),
        });

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const updateWebUser = async (
    accessToken: string,
    targetEmail: string,
    payload: UpdateWebUserPayload,
) => {
    try {
        const response = await apiClient.put<WebUserRecord>(
            `/api/v1/web/users/${encodeURIComponent(targetEmail)}`,
            payload,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const deleteWebUser = async (accessToken: string, targetEmail: string) => {
    try {
        await apiClient.delete(`/api/v1/web/users/${encodeURIComponent(targetEmail)}`, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const unlinkWebUserAccount = async (accessToken: string, targetEmail: string) => {
    try {
        const response = await apiClient.delete<WebUserRecord>(
            `/api/v1/web/users/${encodeURIComponent(targetEmail)}/link`,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

const fetchAllPagedItems = async <T>(
    path: string,
    accessToken: string,
    params?: Record<string, number | string | undefined>,
) => {
    const items: T[] = [];
    let page = 0;

    while (page < MAX_LIST_PAGE_COUNT) {
        const response = await apiClient.get<PaginatedResponse<T>>(path, {
            headers: getAuthHeaders(accessToken),
            params: {
                ...params,
                page,
                size: MAX_LIST_PAGE_SIZE,
            },
        });

        items.push(...response.data.items);

        if (!getPaginatedHasNext(response.data)) {
            return items;
        }

        page += 1;
    }

    throw new ApiError(
        {
            status: 500,
            message: `Pagination exceeded ${MAX_LIST_PAGE_COUNT} pages for ${path}.`,
            details: [],
        },
        500,
    );
};

export const listGroups = async (accessToken: string) => {
    try {
        return await fetchAllPagedItems<GroupListItem>("/api/v1/groups", accessToken);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listAllServerGroups = async (accessToken: string, query?: string) => {
    try {
        return await fetchAllPagedItems<GroupRecord>("/api/v1/groups", accessToken, {
            query,
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listAllTemplates = async (
    accessToken: string,
    params?: {
        group?: string;
        query?: string;
    },
) => {
    try {
        return await fetchAllPagedItems<TemplateRecord>("/api/v1/templates", accessToken, params);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listTemplates = async (
    accessToken: string,
    params?: {
        group?: string;
        query?: string;
        page?: number;
        size?: number;
    },
) => {
    try {
        const response = await apiClient.get<PaginatedResponse<TemplateRecord>>(
            "/api/v1/templates",
            {
                headers: getAuthHeaders(accessToken),
                params,
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const uploadTemplate = async (
    accessToken: string,
    group: string,
    version: string,
    file: File,
) => {
    try {
        const formData = new FormData();
        formData.append("file", file);

        await apiClient.post(`/api/v1/templates/${encodeURIComponent(group)}/upload`, formData, {
            headers: {
                ...getAuthHeaders(accessToken),
                "Content-Type": "multipart/form-data",
            },
            params: {
                version,
            },
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const downloadTemplate = async (accessToken: string, group: string, version: string) => {
    try {
        const response = await apiClient.get<BlobPart>(
            `/api/v1/templates/${encodeURIComponent(group)}/${encodeURIComponent(version)}/download`,
            {
                headers: getAuthHeaders(accessToken),
                responseType: "blob",
            },
        );

        const blob = new Blob([response.data], { type: "application/gzip" });
        const downloadUrl = window.URL.createObjectURL(blob);
        const fileName = `${group}-${version}-template.tar.gz`;
        const link = document.createElement("a");

        link.href = downloadUrl;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(downloadUrl);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const deleteTemplate = async (accessToken: string, group: string, version: string) => {
    try {
        await apiClient.delete(
            `/api/v1/templates/${encodeURIComponent(group)}/${encodeURIComponent(version)}`,
            {
                headers: getAuthHeaders(accessToken),
            },
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const getGroupByName = async (accessToken: string, groupName: string) => {
    try {
        const response = await apiClient.get<GroupRecord>(
            `/api/v1/groups/${encodeURIComponent(groupName)}`,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const restartServerGroup = async (accessToken: string, groupName: string) => {
    try {
        await apiClient.post(`/api/v1/groups/${encodeURIComponent(groupName)}/restart`, undefined, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const createServerGroup = async (accessToken: string, payload: CreateGroupPayload) => {
    try {
        const response = await apiClient.post<GroupRecord>("/api/v1/groups", payload, {
            headers: getAuthHeaders(accessToken),
        });

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const updateServerGroup = async (
    accessToken: string,
    groupName: string,
    payload: UpdateGroupPayload,
) => {
    try {
        const response = await apiClient.put<GroupRecord>(
            `/api/v1/groups/${encodeURIComponent(groupName)}`,
            payload,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const toggleServerGroupMaintenance = async (
    accessToken: string,
    groupName: string,
    maintenance: boolean,
) => {
    try {
        const response = await apiClient.put<GroupRecord>(
            `/api/v1/groups/${encodeURIComponent(groupName)}/maintenance`,
            { maintenance },
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const deleteServerGroup = async (accessToken: string, groupName: string) => {
    try {
        await apiClient.delete(`/api/v1/groups/${encodeURIComponent(groupName)}`, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listServers = async (
    accessToken: string,
    params?: {
        group?: string;
        query?: string;
        page?: number;
        size?: number;
    },
) => {
    try {
        const response = await apiClient.get<PaginatedResponse<ServerRecord>>("/api/v1/servers", {
            headers: getAuthHeaders(accessToken),
            params,
        });

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listAllServers = async (
    accessToken: string,
    params?: {
        group?: string;
        query?: string;
    },
) => {
    try {
        return await fetchAllPagedItems<ServerRecord>("/api/v1/servers", accessToken, params);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const getServerById = async (accessToken: string, serverId: string) => {
    try {
        const response = await apiClient.get<ServerRecord>(
            `/api/v1/servers/${encodeURIComponent(serverId)}`,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listPersistedPlayers = async (
    accessToken: string,
    params?: {
        page?: number;
        size?: number;
        query?: string;
    },
) => {
    try {
        const response = await apiClient.get<PaginatedResponse<PersistedPlayerRecord>>(
            "/api/v1/players/all",
            {
                headers: getAuthHeaders(accessToken),
                params,
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const getPlayerByUuid = async (accessToken: string, playerUuid: string) => {
    try {
        const response = await apiClient.get<PlayerRecord>(
            `/api/v1/players/${encodeURIComponent(playerUuid)}`,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listOnlinePlayers = async (
    accessToken: string,
    params?: {
        page?: number;
        size?: number;
        serverId?: string;
        proxyId?: string;
        query?: string;
        name?: string;
    },
) => {
    try {
        const response = await apiClient.get<PaginatedResponse<OnlinePlayerRecord>>(
            "/api/v1/players",
            {
                headers: getAuthHeaders(accessToken),
                params,
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listAllPermissionGroups = async (accessToken: string, query?: string) => {
    try {
        return await fetchAllPagedItems<PermissionGroupRecord>(
            "/api/v1/permissions/groups",
            accessToken,
            { query },
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const getPermissionGroupByName = async (accessToken: string, groupName: string) => {
    try {
        const response = await apiClient.get<PermissionGroupRecord>(
            `/api/v1/permissions/groups/${encodeURIComponent(groupName)}`,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const createPermissionGroup = async (
    accessToken: string,
    payload: CreatePermissionGroupPayload,
) => {
    try {
        const response = await apiClient.post<PermissionGroupRecord>(
            "/api/v1/permissions/groups",
            payload,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const updatePermissionGroup = async (
    accessToken: string,
    groupName: string,
    payload: UpdatePermissionGroupPayload,
) => {
    try {
        const response = await apiClient.put<PermissionGroupRecord>(
            `/api/v1/permissions/groups/${encodeURIComponent(groupName)}`,
            payload,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const deletePermissionGroup = async (accessToken: string, groupName: string) => {
    try {
        await apiClient.delete(`/api/v1/permissions/groups/${encodeURIComponent(groupName)}`, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const addPermissionToGroup = async (
    accessToken: string,
    groupName: string,
    permission: string,
) => {
    try {
        const response = await apiClient.post<PermissionGroupRecord>(
            `/api/v1/permissions/groups/${encodeURIComponent(groupName)}/permissions/${encodeURIComponent(permission)}`,
            undefined,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const removePermissionFromGroup = async (
    accessToken: string,
    groupName: string,
    permission: string,
) => {
    try {
        const response = await apiClient.delete<PermissionGroupRecord>(
            `/api/v1/permissions/groups/${encodeURIComponent(groupName)}/permissions/${encodeURIComponent(permission)}`,
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const updatePlayerPermissionGroup = async (
    accessToken: string,
    playerUuid: string,
    group: string,
    duration = "-1",
) => {
    try {
        const response = await apiClient.put<PersistedPlayerRecord>(
            `/api/v1/players/${encodeURIComponent(playerUuid)}/group`,
            { group, duration },
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const transferPlayerToTarget = async (
    accessToken: string,
    playerUuid: string,
    target: string,
) => {
    try {
        await apiClient.post(
            `/api/v1/players/${encodeURIComponent(playerUuid)}/transfer`,
            { target },
            {
                headers: getAuthHeaders(accessToken),
            },
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const searchEverything = async (accessToken: string, query: string, limit?: number) => {
    try {
        const response = await apiClient.get<SearchResponse>(
            `/api/v1/search/${encodeURIComponent(query)}`,
            {
                headers: getAuthHeaders(accessToken),
                params: {
                    limit,
                },
            },
        );

        return response.data;
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const requestServerForGroup = async (accessToken: string, group: string, count = 1) => {
    const normalizedCount = Math.max(1, Math.trunc(count));

    try {
        for (let index = 0; index < normalizedCount; index += 1) {
            await apiClient.post(
                "/api/v1/servers/request",
                { group },
                { headers: getAuthHeaders(accessToken) },
            );
        }
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const stopServerGracefully = async (accessToken: string, serverId: string) => {
    try {
        await apiClient.post(`/api/v1/servers/${encodeURIComponent(serverId)}/stop`, undefined, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const forceServerTemplatePush = async (accessToken: string, serverId: string) => {
    try {
        await apiClient.post(
            `/api/v1/servers/${encodeURIComponent(serverId)}/template/push`,
            undefined,
            { headers: getAuthHeaders(accessToken) },
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const killServerInstance = async (accessToken: string, serverId: string) => {
    try {
        await apiClient.post(`/api/v1/servers/${encodeURIComponent(serverId)}/kill`, undefined, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const executeCommand = async (accessToken: string, payload: ExecuteCommandPayload) => {
    try {
        await apiClient.post("/api/v1/command", payload, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
