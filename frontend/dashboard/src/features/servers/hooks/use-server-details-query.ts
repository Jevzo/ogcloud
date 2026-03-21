import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { getGroupByName, getServerById, listOnlinePlayers } from "@/api";
import type { PaginatedResponse } from "@/types/dashboard";
import type { GroupRecord } from "@/types/group";
import type { OnlinePlayerRecord, ServerRecord } from "@/types/server";

const PLAYER_PAGE_SIZE = 5;
const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_PLAYER_PAGE: PaginatedResponse<OnlinePlayerRecord> = {
    items: [],
    page: 0,
    size: PLAYER_PAGE_SIZE,
    totalItems: 0,
};

interface UseServerDetailsQueryOptions {
    playerPageIndex: number;
    serverId: string;
}

interface ServerDetailsQueryResult {
    errorMessage: string | null;
    group: GroupRecord | null;
    isLoading: boolean;
    isRefreshing: boolean;
    playerPage: PaginatedResponse<OnlinePlayerRecord>;
    refresh: (showLoading?: boolean) => Promise<void>;
    refreshIntervalMs: number;
    runtimeSnapshot: ServerRecord | null;
    server: ServerRecord | null;
}

export const useServerDetailsQuery = ({
    playerPageIndex,
    serverId,
}: UseServerDetailsQueryOptions): ServerDetailsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);
    const runtimeRequestIdRef = useRef(0);

    const [server, setServer] = useState<ServerRecord | null>(null);
    const [runtimeSnapshot, setRuntimeSnapshot] = useState<ServerRecord | null>(null);
    const [group, setGroup] = useState<GroupRecord | null>(null);
    const [playerPage, setPlayerPage] =
        useState<PaginatedResponse<OnlinePlayerRecord>>(EMPTY_PLAYER_PAGE);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);

    const refresh = useCallback(
        async (showLoading = false) => {
            if (!serverId) {
                setServer(null);
                setRuntimeSnapshot(null);
                setGroup(null);
                setPlayerPage(EMPTY_PLAYER_PAGE);
                setErrorMessage("Missing server ID.");
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
                const nextServer = await getServerById(accessToken, serverId);
                const [nextPlayerPage, nextGroup] = await Promise.all([
                    listOnlinePlayers(accessToken, {
                        page: playerPageIndex,
                        proxyId: nextServer.type === "PROXY" ? serverId : undefined,
                        serverId: nextServer.type === "PROXY" ? undefined : serverId,
                        size: PLAYER_PAGE_SIZE,
                    }),
                    getGroupByName(accessToken, nextServer.group),
                ]);

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setServer(nextServer);
                setRuntimeSnapshot(nextServer);
                setGroup(nextGroup);
                setPlayerPage(nextPlayerPage);
                setErrorMessage(null);
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to load this server.",
                );
            } finally {
                if (requestIdRef.current === requestId) {
                    setIsLoading(false);
                    setIsRefreshing(false);
                }
            }
        },
        [getAccessToken, playerPageIndex, serverId],
    );

    const refreshRuntimeSnapshot = useCallback(async () => {
        if (!serverId) {
            return;
        }

        const requestId = runtimeRequestIdRef.current + 1;
        runtimeRequestIdRef.current = requestId;

        try {
            const accessToken = await getAccessToken();
            const nextServer = await getServerById(accessToken, serverId);

            if (runtimeRequestIdRef.current !== requestId) {
                return;
            }

            setRuntimeSnapshot(nextServer);
        } catch {
        }
    }, [getAccessToken, serverId]);

    useEffect(() => {
        void refresh(true);
    }, [refresh]);

    useEffect(() => {
        if (!serverId) {
            return;
        }

        const intervalId = window.setInterval(() => {
            void refreshRuntimeSnapshot();
        }, REFRESH_INTERVAL_MS);

        return () => {
            window.clearInterval(intervalId);
            runtimeRequestIdRef.current += 1;
        };
    }, [refreshRuntimeSnapshot, serverId]);

    useEffect(() => {
        return () => {
            requestIdRef.current += 1;
            runtimeRequestIdRef.current += 1;
        };
    }, []);

    return {
        errorMessage,
        group,
        isLoading,
        isRefreshing,
        playerPage,
        refresh,
        refreshIntervalMs: REFRESH_INTERVAL_MS,
        runtimeSnapshot,
        server,
    };
};
