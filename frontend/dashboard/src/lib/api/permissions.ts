import { permissionGroupRecordSchema } from "@/features/permissions/schemas";
import type {
    CreatePermissionGroupPayload,
    PermissionGroupRecord,
    UpdatePermissionGroupPayload,
} from "@/types/permission";

import {
    apiClient,
    fetchAllPagedItems,
    getAuthHeaders,
    parseWithSchema,
    SESSION_EXPIRED_MESSAGE,
    toApiError,
} from "./shared";

export const listAllPermissionGroups = async (accessToken: string, query?: string) => {
    try {
        return await fetchAllPagedItems<PermissionGroupRecord>(
            "/api/v1/permissions/groups",
            accessToken,
            { query },
            permissionGroupRecordSchema,
            "Received an invalid permission group list response.",
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

        return parseWithSchema(
            permissionGroupRecordSchema,
            response.data,
            "Received an invalid permission group details response.",
        );
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

        return parseWithSchema(
            permissionGroupRecordSchema,
            response.data,
            "Received an invalid permission group response after creation.",
        );
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

        return parseWithSchema(
            permissionGroupRecordSchema,
            response.data,
            "Received an invalid permission group response after update.",
        );
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

        return parseWithSchema(
            permissionGroupRecordSchema,
            response.data,
            "Received an invalid permission group response after adding a permission.",
        );
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

        return parseWithSchema(
            permissionGroupRecordSchema,
            response.data,
            "Received an invalid permission group response after removing a permission.",
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
