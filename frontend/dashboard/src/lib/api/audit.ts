import type { ApiAuditLogRecord } from "@/types/audit";
import type { PaginatedResponse } from "@/types/dashboard";

import { apiClient, getAuthHeaders, SESSION_EXPIRED_MESSAGE, toApiError } from "./shared";

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
