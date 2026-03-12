import type { DashboardOverviewResponse, DashboardOverviewScalingAction } from "@/types/dashboard";

import { apiClient, getAuthHeaders, SESSION_EXPIRED_MESSAGE, toApiError } from "./shared";

type DashboardOverviewApiResponse = Omit<DashboardOverviewResponse, "scalingActions"> & {
    scalingActions?: DashboardOverviewScalingAction[] | null;
};

const normalizeDashboardOverview = (
    payload: DashboardOverviewApiResponse,
): DashboardOverviewResponse => ({
    ...payload,
    scalingActions: Array.isArray(payload.scalingActions) ? payload.scalingActions : [],
});

export const getDashboardOverview = async (accessToken: string) => {
    const startedAt = performance.now();

    try {
        const response = await apiClient.get<DashboardOverviewApiResponse>(
            "/api/v1/dashboard/overview",
            {
                headers: getAuthHeaders(accessToken),
            },
        );

        return {
            data: normalizeDashboardOverview(response.data),
            latencyMs: Math.max(1, Math.round(performance.now() - startedAt)),
        };
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
