import { onlinePlayersPageSchema } from "@/features/servers/schemas";
import { persistedPlayerRecordSchema, playerRecordSchema } from "@/features/players/schemas";
import type { PaginatedResponse } from "@/types/dashboard";
import type { PersistedPlayerRecord, PlayerRecord } from "@/types/player";
import type { OnlinePlayerRecord } from "@/types/server";

import {
    apiClient,
    createPaginatedResponseSchema,
    parseWithSchema,
    getAuthHeaders,
    SESSION_EXPIRED_MESSAGE,
    toApiError,
} from "./shared";

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

        return parseWithSchema(
            createPaginatedResponseSchema(persistedPlayerRecordSchema),
            response.data,
            "Received an invalid persisted players response.",
        );
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

        return parseWithSchema(
            playerRecordSchema,
            response.data,
            "Received an invalid player response.",
        );
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

        return parseWithSchema(
            onlinePlayersPageSchema,
            response.data,
            "Received an invalid online players response.",
        );
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
        const response = await apiClient.put<PlayerRecord>(
            `/api/v1/players/${encodeURIComponent(playerUuid)}/group`,
            { group, duration },
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return parseWithSchema(
            playerRecordSchema,
            response.data,
            "Received an invalid player response after updating the permission group.",
        );
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
