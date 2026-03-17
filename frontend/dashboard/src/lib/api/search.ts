import { searchResponseSchema } from "@/features/search/schemas";

import {
    apiClient,
    getAuthHeaders,
    parseWithSchema,
    SESSION_EXPIRED_MESSAGE,
    toApiError,
} from "./shared";

export const searchEverything = async (accessToken: string, query: string, limit?: number) => {
    try {
        const response = await apiClient.get(`/api/v1/search/${encodeURIComponent(query)}`, {
            headers: getAuthHeaders(accessToken),
            params: {
                limit,
            },
        });

        return parseWithSchema(
            searchResponseSchema,
            response.data,
            "Received an invalid search response.",
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
