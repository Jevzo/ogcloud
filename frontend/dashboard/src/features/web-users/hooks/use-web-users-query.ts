import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { listWebUsers } from "@/api";
import type { PaginatedResponse } from "@/types/dashboard";
import type { WebUserRecord } from "@/types/web-user";

const WEB_USER_PAGE_SIZE = 10;
const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_WEB_USER_PAGE: PaginatedResponse<WebUserRecord> = {
    items: [],
    page: 0,
    size: WEB_USER_PAGE_SIZE,
    totalItems: 0,
};

interface UseWebUsersQueryOptions {
    currentPage: number;
    enabled: boolean;
    query: string;
}

interface WebUsersQueryResult {
    data: PaginatedResponse<WebUserRecord>;
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
    refreshIntervalMs: number;
}

export const useWebUsersQuery = ({
    currentPage,
    enabled,
    query,
}: UseWebUsersQueryOptions): WebUsersQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<PaginatedResponse<WebUserRecord>>(EMPTY_WEB_USER_PAGE);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(enabled);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);

    const refresh = useCallback(
        async (showLoading = false) => {
            if (!enabled) {
                requestIdRef.current += 1;
                setData(EMPTY_WEB_USER_PAGE);
                setErrorMessage(null);
                setIsLoading(false);
                setIsRefreshing(false);
                setLastUpdatedAt(null);
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
                const nextPage = await listWebUsers(accessToken, {
                    page: currentPage,
                    query: query || undefined,
                    size: WEB_USER_PAGE_SIZE,
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

                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to load web users.",
                );
            } finally {
                if (requestIdRef.current === requestId) {
                    setIsLoading(false);
                    setIsRefreshing(false);
                }
            }
        },
        [currentPage, enabled, getAccessToken, query],
    );

    useEffect(() => {
        if (!enabled) {
            requestIdRef.current += 1;
            setData(EMPTY_WEB_USER_PAGE);
            setErrorMessage(null);
            setIsLoading(false);
            setIsRefreshing(false);
            setLastUpdatedAt(null);
            return;
        }

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
    }, [enabled, refresh]);

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
