import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/hooks/use-access-token";
import { listAllServerGroups } from "@/lib/api";
import type { GroupRecord } from "@/types/group";

const REFRESH_INTERVAL_MS = 10_000;
const EMPTY_GROUPS: GroupRecord[] = [];

interface UseGroupsQueryOptions {
    query: string;
}

interface GroupsQueryResult {
    data: GroupRecord[];
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
    refreshIntervalMs: number;
}

const sortGroups = (groups: GroupRecord[]) =>
    [...groups].sort((left, right) => left.id.localeCompare(right.id));

export const useGroupsQuery = ({ query }: UseGroupsQueryOptions): GroupsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<GroupRecord[]>(EMPTY_GROUPS);
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
                const nextGroups = await listAllServerGroups(accessToken, query || undefined);

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setData(sortGroups(nextGroups));
                setErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setErrorMessage(error instanceof Error ? error.message : "Unable to load groups.");
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
