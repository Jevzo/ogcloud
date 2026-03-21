import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { getGroupByName, listServers } from "@/api";
import type { GroupRecord } from "@/types/group";

interface GroupDetailsQueryResult {
    currentOnlineCount: number | null;
    errorMessage: string | null;
    group: GroupRecord | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    refresh: (showLoading?: boolean) => Promise<void>;
}

export const useGroupDetailsQuery = (groupName: string): GroupDetailsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [group, setGroup] = useState<GroupRecord | null>(null);
    const [currentOnlineCount, setCurrentOnlineCount] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);

    const refresh = useCallback(
        async (showLoading = false) => {
            if (!groupName) {
                setGroup(null);
                setCurrentOnlineCount(null);
                setErrorMessage("Missing group name.");
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
                const [nextGroup, groupServers] = await Promise.all([
                    getGroupByName(accessToken, groupName),
                    listServers(accessToken, {
                        group: groupName,
                        page: 0,
                        size: 200,
                    }),
                ]);

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setGroup(nextGroup);
                setCurrentOnlineCount(
                    groupServers.items.filter((server) => server.state.toUpperCase() === "RUNNING")
                        .length,
                );
                setErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setErrorMessage(error instanceof Error ? error.message : "Unable to load group.");
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
        currentOnlineCount,
        errorMessage,
        group,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
    };
};
