import { useCallback } from "react";

import { useAuthStore } from "@/store/auth-store";

const SESSION_EXPIRED_MESSAGE = "Your session expired. Please sign in again.";

export const useAccessToken = () => {
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);

    return useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error(SESSION_EXPIRED_MESSAGE);
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);
};
