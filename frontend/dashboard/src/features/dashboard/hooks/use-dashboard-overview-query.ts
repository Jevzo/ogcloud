import { useCallback, useEffect, useRef, useState } from "react";

import { EMPTY_DASHBOARD_OVERVIEW, type DashboardOverview } from "@/features/dashboard/schemas";
import { API_LATENCY_WINDOW_MS, getAverageApiLatency } from "@/api/latency";
import { getDashboardOverview } from "@/api";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";

const REFRESH_INTERVAL_MS = 10_000;

interface DashboardOverviewQueryResult {
    data: DashboardOverview;
    averageLatencyMs: number | null;
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refreshIntervalMs: number;
    averageLatencyWindowMs: number;
    refresh: (showLoading?: boolean) => Promise<void>;
}

export const useDashboardOverviewQuery = (): DashboardOverviewQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<DashboardOverview>(EMPTY_DASHBOARD_OVERVIEW);
    const [averageLatencyMs, setAverageLatencyMs] = useState<number | null>(getAverageApiLatency());
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
                const nextOverview = await getDashboardOverview(accessToken);

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setData(nextOverview.data);
                setAverageLatencyMs(getAverageApiLatency());
                setErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setAverageLatencyMs(getAverageApiLatency());
                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to load dashboard overview.",
                );
            } finally {
                if (requestIdRef.current === requestId) {
                    setIsLoading(false);
                    setIsRefreshing(false);
                }
            }
        },
        [getAccessToken],
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
        averageLatencyMs,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refreshIntervalMs: REFRESH_INTERVAL_MS,
        averageLatencyWindowMs: API_LATENCY_WINDOW_MS,
        refresh,
    };
};
