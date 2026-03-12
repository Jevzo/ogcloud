import type { PaginatedResponse } from "@/types/dashboard";
import type {
    CreateWebUserPayload,
    UpdateWebUserPayload,
    WebUserRecord,
} from "@/types/web-user";

import { apiClient, getAuthHeaders, SESSION_EXPIRED_MESSAGE, toApiError } from "./shared";

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
