import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/hooks/use-access-token";
import { listAllServerGroups, listGroups } from "@/lib/api";
import type { GroupListItem } from "@/types/dashboard";
import type { GroupRecord } from "@/types/group";

const REFRESH_INTERVAL_MS = 10_000;

interface TemplateMetadataQueryResult {
    data: {
        groups: GroupListItem[];
        serverGroups: GroupRecord[];
    };
    errorMessage: string | null;
    isLoading: boolean;
    lastUpdatedAt: number | null;
    refresh: () => Promise<void>;
}

const EMPTY_TEMPLATE_METADATA = {
    groups: [],
    serverGroups: [],
};

export const useTemplateMetadataQuery = (): TemplateMetadataQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<{
        groups: GroupListItem[];
        serverGroups: GroupRecord[];
    }>(EMPTY_TEMPLATE_METADATA);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);

    const refresh = useCallback(async () => {
        const requestId = requestIdRef.current + 1;
        requestIdRef.current = requestId;
        setIsLoading(true);

        try {
            const accessToken = await getAccessToken();
            const [groups, serverGroups] = await Promise.all([
                listGroups(accessToken),
                listAllServerGroups(accessToken),
            ]);

            if (requestIdRef.current !== requestId) {
                return;
            }

            setData({ groups, serverGroups });
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
            }
        }
    }, [getAccessToken]);

    useEffect(() => {
        let mounted = true;

        const runRefresh = async () => {
            if (!mounted) {
                return;
            }

            await refresh();
        };

        void runRefresh();

        const intervalId = window.setInterval(() => {
            void runRefresh();
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
        lastUpdatedAt,
        refresh,
    };
};
