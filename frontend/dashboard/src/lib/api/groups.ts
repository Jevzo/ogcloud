import type { GroupListItem } from "@/types/dashboard";
import type { CreateGroupPayload, GroupRecord, UpdateGroupPayload } from "@/types/group";

import {
    apiClient,
    fetchAllPagedItems,
    getAuthHeaders,
    SESSION_EXPIRED_MESSAGE,
    toApiError,
} from "./shared";

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
