import { useDeferredValue, useEffect, useRef, useState } from "react";

import { createEmptySearchResponse, type SearchResponse } from "@/features/search/schemas";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { searchEverything } from "@/api";

const DEFAULT_DEBOUNCE_MS = 180;

interface UseGlobalSearchQueryOptions {
    limit: number;
    query: string;
    debounceMs?: number;
}

interface UseGlobalSearchQueryResult {
    errorMessage: string | null;
    isSearching: boolean;
    results: SearchResponse;
    trimmedQuery: string;
    totalResultCount: number;
}

export const useGlobalSearchQuery = ({
    limit,
    query,
    debounceMs = DEFAULT_DEBOUNCE_MS,
}: UseGlobalSearchQueryOptions): UseGlobalSearchQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);
    const deferredQuery = useDeferredValue(query);
    const trimmedQuery = deferredQuery.trim();

    const [results, setResults] = useState<SearchResponse>(createEmptySearchResponse(limit));
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isSearching, setIsSearching] = useState(false);

    useEffect(() => {
        if (!trimmedQuery) {
            requestIdRef.current += 1;
            setIsSearching(false);
            setResults(createEmptySearchResponse(limit));
            setErrorMessage(null);
            return;
        }

        const requestId = requestIdRef.current + 1;
        requestIdRef.current = requestId;

        const timeoutId = window.setTimeout(() => {
            setIsSearching(true);

            void (async () => {
                try {
                    const accessToken = await getAccessToken();
                    const nextResults = await searchEverything(accessToken, trimmedQuery, limit);

                    if (requestIdRef.current !== requestId) {
                        return;
                    }

                    setResults(nextResults);
                    setErrorMessage(null);
                } catch (error) {
                    if (requestIdRef.current !== requestId) {
                        return;
                    }

                    setResults(createEmptySearchResponse(limit));
                    setErrorMessage(
                        error instanceof Error ? error.message : "Unable to search right now.",
                    );
                } finally {
                    if (requestIdRef.current === requestId) {
                        setIsSearching(false);
                    }
                }
            })();
        }, debounceMs);

        return () => {
            window.clearTimeout(timeoutId);
        };
    }, [debounceMs, getAccessToken, limit, trimmedQuery]);

    return {
        errorMessage,
        isSearching,
        results,
        trimmedQuery,
        totalResultCount: results.groups.length + results.servers.length + results.players.length,
    };
};
