import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/hooks/use-access-token";
import { listAllTemplates } from "@/lib/api";
import type { TemplateRecord } from "@/types/template";

const EMPTY_TEMPLATES: TemplateRecord[] = [];

interface GroupFormOptionsQueryResult {
    data: TemplateRecord[];
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
}

const sortTemplates = (templates: TemplateRecord[]) =>
    [...templates].sort(
        (left, right) =>
            left.group.localeCompare(right.group) || left.version.localeCompare(right.version),
    );

export const useGroupFormOptionsQuery = (): GroupFormOptionsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<TemplateRecord[]>(EMPTY_TEMPLATES);
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
                const templates = await listAllTemplates(accessToken);

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setData(sortTemplates(templates));
                setErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to load the template catalog.",
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
        void refresh(true);

        return () => {
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
    };
};
