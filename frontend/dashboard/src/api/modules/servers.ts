import {
    serverRecordSchema,
    serverRequestResponseSchema,
    serversPageSchema,
    type ServerRequestResponseSchema,
} from "@/features/servers/schemas";
import type { PaginatedResponse } from "@/types/dashboard";
import type { ServerRecord } from "@/types/server";

import {
    apiClient,
    fetchAllPagedItems,
    parseWithSchema,
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

        return parseWithSchema(
            serversPageSchema,
            response.data,
            "Received an invalid server list response.",
        );
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
        return await fetchAllPagedItems<ServerRecord>(
            "/api/v1/servers",
            accessToken,
            params,
            serverRecordSchema,
            "Received an invalid server response while loading all servers.",
        );
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

        return parseWithSchema(
            serverRecordSchema,
            response.data,
            "Received an invalid server details response.",
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const requestServerForGroup = async (accessToken: string, group: string, count = 1) => {
    const normalizedCount = Math.max(1, Math.trunc(count));
    const responses: ServerRequestResponseSchema[] = [];

    try {
        for (let index = 0; index < normalizedCount; index += 1) {
            const response = await apiClient.post(
                "/api/v1/servers/request",
                { group },
                { headers: getAuthHeaders(accessToken) },
            );

            responses.push(
                parseWithSchema(
                    serverRequestResponseSchema,
                    response.data,
                    "Received an invalid server request response.",
                ),
            );
        }

        return responses;
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
