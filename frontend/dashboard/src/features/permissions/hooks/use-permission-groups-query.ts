import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/hooks/use-access-token";
import { listAllPermissionGroups } from "@/lib/api";
import type { PermissionGroupRecord } from "@/types/permission";

const REFRESH_INTERVAL_MS = 10_000;
const EMPTY_PERMISSION_GROUPS: PermissionGroupRecord[] = [];

interface UsePermissionGroupsQueryOptions {
    query: string;
}

interface PermissionGroupsQueryResult {
    data: PermissionGroupRecord[];
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
    refreshIntervalMs: number;
}

const sortPermissionGroups = (groups: PermissionGroupRecord[]) =>
    [...groups].sort(
        (left, right) => left.weight - right.weight || left.name.localeCompare(right.name),
    );

export const usePermissionGroupsQuery = ({
    query,
}: UsePermissionGroupsQueryOptions): PermissionGroupsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<PermissionGroupRecord[]>(EMPTY_PERMISSION_GROUPS);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);

    const refresh = useCallback(
        async (showLoading = false) => {
            const requestId = requestIdRef.current + 1;
            requestIdRef.current = requestId;

            if (showLoading) {
                setIsLoading(true);
            } else {
                setIsRefreshing(true);
            }

            try {
                const accessToken = await getAccessToken();
                const nextGroups = await listAllPermissionGroups(accessToken, query || undefined);

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setData(sortPermissionGroups(nextGroups));
                setErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setErrorMessage(
                    error instanceof Error
                        ? error.message
                        : "Unable to load permission groups.",
                );
            } finally {
                if (requestIdRef.current === requestId) {
                    setIsLoading(false);
                    setIsRefreshing(false);
                }
            }
        },
        [getAccessToken, query],
    );

    useEffect(() => {
        let mounted = true;

        const runRefresh = async (showLoading = false) => {
            if (!mounted) {
                return;
            }

            await refresh(showLoading);
        };

        void runRefresh(true);

        const intervalId = window.setInterval(() => {
            void runRefresh(false);
        }, REFRESH_INTERVAL_MS);

        return () => {
            mounted = false;
            window.clearInterval(intervalId);
            requestIdRef.current += 1;
        };
    }, [refresh]);

    return {
        data,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
        refreshIntervalMs: REFRESH_INTERVAL_MS,
    };
};
