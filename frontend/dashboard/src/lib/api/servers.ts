import type { PaginatedResponse } from "@/types/dashboard";
import type { ServerRecord } from "@/types/server";

import {
    apiClient,
    fetchAllPagedItems,
    getAuthHeaders,
    SESSION_EXPIRED_MESSAGE,
    toApiError,
} from "./shared";

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
