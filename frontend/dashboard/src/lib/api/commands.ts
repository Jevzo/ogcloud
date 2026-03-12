import type { ExecuteCommandPayload } from "@/types/command";

import { apiClient, getAuthHeaders, SESSION_EXPIRED_MESSAGE, toApiError } from "./shared";

export const executeCommand = async (accessToken: string, payload: ExecuteCommandPayload) => {
    try {
        await apiClient.post("/api/v1/command", payload, {
            headers: getAuthHeaders(accessToken),
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
