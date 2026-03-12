import type { SearchResponse } from "@/types/search";

import { apiClient, getAuthHeaders, SESSION_EXPIRED_MESSAGE, toApiError } from "./shared";

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
