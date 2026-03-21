import { dashboardOverviewSchema } from "@/features/dashboard/schemas";

import {
    apiClient,
    getAuthHeaders,
    parseWithSchema,
    SESSION_EXPIRED_MESSAGE,
    toApiError,
} from "./shared";

export const getDashboardOverview = async (accessToken: string) => {
    const startedAt = performance.now();

    try {
        const response = await apiClient.get("/api/v1/dashboard/overview", {
            headers: getAuthHeaders(accessToken),
        });

        return {
            data: parseWithSchema(
                dashboardOverviewSchema,
                response.data,
                "Received an invalid dashboard overview response.",
            ),
            latencyMs: Math.max(1, Math.round(performance.now() - startedAt)),
        };
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
