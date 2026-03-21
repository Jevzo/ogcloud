import { useCallback, useEffect, useRef, useState } from "react";

import { getPlayerByUuid } from "@/api";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import type { PlayerRecord } from "@/types/player";

const REFRESH_INTERVAL_MS = 10_000;

interface PlayerDetailsQueryResult {
    errorMessage: string | null;
    isLoading: boolean;
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
    player: PlayerRecord | null;
    refresh: (showLoading?: boolean) => Promise<void>;
}

export const usePlayerDetailsQuery = (playerUuid: string): PlayerDetailsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [player, setPlayer] = useState<PlayerRecord | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);

    const refresh = useCallback(
        async (showLoading = false) => {
            if (!playerUuid) {
                setPlayer(null);
                setErrorMessage("Missing player UUID.");
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
                const nextPlayer = await getPlayerByUuid(accessToken, playerUuid);

                if (requestIdRef.current !== requestId) {
                    return;
                }

                setPlayer(nextPlayer);
                setErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                if (requestIdRef.current !== requestId) {
                    return;
                }

                setErrorMessage(error instanceof Error ? error.message : "Unable to load player.");
            } finally {
                if (requestIdRef.current === requestId) {
                    setIsLoading(false);
                    setIsRefreshing(false);
                }
            }
        },
        [getAccessToken, playerUuid],
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
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        player,
        refresh,
    };
};
