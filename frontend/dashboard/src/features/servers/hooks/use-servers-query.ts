import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { listServers } from "@/api";
import type { PaginatedResponse } from "@/types/dashboard";
import type { ServerRecord } from "@/types/server";

const SERVER_PAGE_SIZE = 10;
const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_SERVER_PAGE: PaginatedResponse<ServerRecord> = {
    items: [],
    page: 0,
    size: SERVER_PAGE_SIZE,
    totalItems: 0,
};

interface UseServersQueryOptions {
    currentPage: number;
    groupFilter: string;
    query: string;
}

interface ServersQueryResult {
    data: PaginatedResponse<ServerRecord>;
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
    refreshIntervalMs: number;
}

export const useServersQuery = ({
    currentPage,
    groupFilter,
    query,
}: UseServersQueryOptions): ServersQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<PaginatedResponse<ServerRecord>>(EMPTY_SERVER_PAGE);
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
                const nextPage = await listServers(accessToken, {
                    group: groupFilter || undefined,
                    page: currentPage,
                    query: query || undefined,
                    size: SERVER_PAGE_SIZE,
                });

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setData(nextPage);
                setErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setErrorMessage(error instanceof Error ? error.message : "Unable to load servers.");
            } finally {
                if (requestIdRef.current === requestId) {
                    setIsLoading(false);
                    setIsRefreshing(false);
                }
            }
        },
        [currentPage, getAccessToken, groupFilter, query],
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
