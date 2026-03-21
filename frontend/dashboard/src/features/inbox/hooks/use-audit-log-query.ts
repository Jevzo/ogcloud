import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { listApiAuditLogs } from "@/api";
import type { ApiAuditLogRecord } from "@/types/audit";
import type { PaginatedResponse } from "@/types/dashboard";

const AUDIT_PAGE_SIZE = 10;

const EMPTY_AUDIT_PAGE: PaginatedResponse<ApiAuditLogRecord> = {
    items: [],
    page: 0,
    size: AUDIT_PAGE_SIZE,
    totalItems: 0,
};

interface UseAuditLogQueryOptions {
    currentPage: number;
    enabled: boolean;
    query: string;
}

interface AuditLogQueryResult {
    data: PaginatedResponse<ApiAuditLogRecord>;
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
}

export const useAuditLogQuery = ({
    currentPage,
    enabled,
    query,
}: UseAuditLogQueryOptions): AuditLogQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<PaginatedResponse<ApiAuditLogRecord>>(EMPTY_AUDIT_PAGE);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(enabled);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);

    const refresh = useCallback(
        async (showLoading = false) => {
            if (!enabled) {
                requestIdRef.current += 1;
                setData(EMPTY_AUDIT_PAGE);
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
                const nextPage = await listApiAuditLogs(accessToken, {
                    page: currentPage,
                    query: query || undefined,
                    size: AUDIT_PAGE_SIZE,
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
                    error instanceof Error ? error.message : "Unable to load API audit logs.",
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
            setData(EMPTY_AUDIT_PAGE);
            setErrorMessage(null);
            setIsLoading(false);
            setIsRefreshing(false);
            setLastUpdatedAt(null);
            return;
        }

        let mounted = true;

        const runRefresh = async () => {
            if (!mounted) {
                return;
            }

            await refresh(true);
        };

        void runRefresh();

        return () => {
            mounted = false;
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
    };
};
