import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { getPermissionGroupByName } from "@/api";
import type { PermissionGroupRecord } from "@/types/permission";

interface PermissionGroupDetailsQueryResult {
    errorMessage: string | null;
    group: PermissionGroupRecord | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
}

export const usePermissionGroupDetailsQuery = (
    groupName: string,
): PermissionGroupDetailsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [group, setGroup] = useState<PermissionGroupRecord | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);

    const refresh = useCallback(
        async (showLoading = false) => {
            if (!groupName) {
                setGroup(null);
                setErrorMessage("Missing permission group name.");
                setIsLoading(false);
                setIsRefreshing(false);
                return;
            }

            const requestId = requestIdRef.current + 1;
            requestIdRef.current = requestId;

            if (showLoading) {
                setIsLoading(true);
            } else {
                setIsRefreshing(true);
            }

            try {
                const accessToken = await getAccessToken();
                const nextGroup = await getPermissionGroupByName(accessToken, groupName);

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setGroup(nextGroup);
                setErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to load permission group.",
                );
            } finally {
                if (requestIdRef.current === requestId) {
                    setIsLoading(false);
                    setIsRefreshing(false);
                }
            }
        },
        [getAccessToken, groupName],
    );

    useEffect(() => {
        void refresh(true);

        return () => {
            requestIdRef.current += 1;
        };
    }, [refresh]);

    return {
        errorMessage,
        group,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
    };
};
