import type { RuntimeBundleScope } from "@/types/runtime";

import { apiClient, getAuthHeaders, SESSION_EXPIRED_MESSAGE, toApiError } from "./shared";

export const requestRuntimeRefresh = async (
    accessToken: string,
    scope: RuntimeBundleScope,
) => {
    try {
        await apiClient.post(
            "/api/v1/runtime/refresh",
            { scope },
            {
                headers: getAuthHeaders(accessToken),
            },
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
