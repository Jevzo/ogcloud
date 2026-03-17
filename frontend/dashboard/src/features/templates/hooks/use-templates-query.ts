import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/hooks/use-access-token";
import { listTemplates } from "@/lib/api";
import type { PaginatedResponse } from "@/types/dashboard";
import type { TemplateRecord } from "@/types/template";

const TEMPLATE_PAGE_SIZE = 10;
const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_TEMPLATE_PAGE: PaginatedResponse<TemplateRecord> = {
    items: [],
    page: 0,
    size: TEMPLATE_PAGE_SIZE,
    totalItems: 0,
};

interface UseTemplatesQueryOptions {
    currentPage: number;
    groupFilter: string;
    query: string;
}

interface TemplatesQueryResult {
    data: PaginatedResponse<TemplateRecord>;
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
    refreshIntervalMs: number;
}

export const useTemplatesQuery = ({
    currentPage,
    groupFilter,
    query,
}: UseTemplatesQueryOptions): TemplatesQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<PaginatedResponse<TemplateRecord>>(EMPTY_TEMPLATE_PAGE);
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
                const nextPage = await listTemplates(accessToken, {
                    group: groupFilter || undefined,
                    page: currentPage,
                    query: query || undefined,
                    size: TEMPLATE_PAGE_SIZE,
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
                    error instanceof Error ? error.message : "Unable to load templates.",
                );
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
