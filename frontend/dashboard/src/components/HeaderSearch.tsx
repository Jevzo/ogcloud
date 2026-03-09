import {startTransition, useCallback, useEffect, useMemo, useRef, useState,} from "react";
import {motion} from "motion/react";
import {FiActivity, FiLayers, FiSearch, FiUsers} from "react-icons/fi";

import AppToasts from "@/components/AppToasts";
import PlayerManagementModal from "@/components/PlayerManagementModal";
import {getPlayerByUuid, searchEverything} from "@/lib/api";
import {formatDateTime} from "@/lib/server-display";
import {useAuthStore} from "@/store/auth-store";
import type {PersistedPlayerRecord} from "@/types/player";
import type {SearchResponse} from "@/types/search";
import {useNavigate} from "react-router";

const SEARCH_RESULT_LIMIT = 6;

const EMPTY_RESULTS: SearchResponse = {
    query: "",
    limit: SEARCH_RESULT_LIMIT,
    groups: [],
    servers: [],
    players: [],
};

const HeaderSearch = () => {
    const navigate = useNavigate();
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const rootRef = useRef<HTMLDivElement | null>(null);
    const searchRequestIdRef = useRef(0);

    const [query, setQuery] = useState("");
    const [results, setResults] = useState<SearchResponse>(EMPTY_RESULTS);
    const [isOpen, setIsOpen] = useState(false);
    const [isSearching, setIsSearching] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [selectedPlayer, setSelectedPlayer] = useState<PersistedPlayerRecord | null>(null);
    const [loadingPlayerUuid, setLoadingPlayerUuid] = useState<string | null>(null);

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const resetSearchSurface = useCallback(() => {
        setIsOpen(false);
        setQuery("");
        setResults(EMPTY_RESULTS);
    }, []);

    useEffect(() => {
        if (!isOpen && !selectedPlayer) {
            return;
        }

        const handlePointerDown = (event: MouseEvent) => {
            if (!rootRef.current?.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };

        const handleEscape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setIsOpen(false);
            }
        };

        window.addEventListener("mousedown", handlePointerDown);
        window.addEventListener("keydown", handleEscape);

        return () => {
            window.removeEventListener("mousedown", handlePointerDown);
            window.removeEventListener("keydown", handleEscape);
        };
    }, [isOpen, selectedPlayer]);

    useEffect(() => {
        const trimmedQuery = query.trim();

        if (!trimmedQuery) {
            searchRequestIdRef.current += 1;
            setIsSearching(false);
            setResults(EMPTY_RESULTS);
            setErrorMessage(null);
            setIsOpen(false);
            return;
        }

        const currentRequestId = searchRequestIdRef.current + 1;
        searchRequestIdRef.current = currentRequestId;
        setIsSearching(true);
        setIsOpen(true);

        void (async () => {
            try {
                const accessToken = await getValidAccessToken();
                const nextResults = await searchEverything(
                    accessToken,
                    trimmedQuery,
                    SEARCH_RESULT_LIMIT
                );

                if (searchRequestIdRef.current !== currentRequestId) {
                    return;
                }

                setResults(nextResults);
                setErrorMessage(null);
            } catch (error) {
                if (searchRequestIdRef.current !== currentRequestId) {
                    return;
                }

                setResults(EMPTY_RESULTS);
                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to search right now."
                );
            } finally {
                if (searchRequestIdRef.current === currentRequestId) {
                    setIsSearching(false);
                }
            }
        })();
    }, [getValidAccessToken, query]);

    const totalResultCount = useMemo(
        () => results.groups.length + results.servers.length + results.players.length,
        [results]
    );

    const handleNavigate = useCallback((targetPath: string) => {
        resetSearchSurface();
        startTransition(() => {
            navigate(targetPath);
        });
    }, [navigate, resetSearchSurface]);

    const handleOpenPlayer = useCallback(async (playerUuid: string) => {
        setLoadingPlayerUuid(playerUuid);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            const player = await getPlayerByUuid(accessToken, playerUuid);

            setSelectedPlayer(player);
            setIsOpen(false);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to load that player."
            );
        } finally {
            setLoadingPlayerUuid(null);
        }
    }, [getValidAccessToken]);

    return (
        <>
            <AppToasts
                items={
                    errorMessage
                        ? [
                            {
                                id: "header-search-error",
                                message: errorMessage,
                                onDismiss: () => setErrorMessage(null),
                                tone: "error" as const,
                            },
                        ]
                        : []
                }
            />

            <div ref={rootRef} className="relative w-full max-w-md">
                <FiSearch
                    className="pointer-events-none absolute left-3 top-1/2 z-10 h-5 w-5 -translate-y-1/2 text-slate-400"/>
                <input
                    type="text"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    onFocus={() => {
                        if (query.trim()) {
                            setIsOpen(true);
                        }
                    }}
                    placeholder="Search instances, players, or groups..."
                    className="app-input-field w-full rounded-lg pl-10 pr-4 text-sm text-slate-100"
                />

                {isOpen && query.trim() ? (
                    <motion.div
                        initial={{y: 8, opacity: 0}}
                        animate={{y: 0, opacity: 1}}
                        transition={{duration: 0.18, ease: "easeOut"}}
                        className="absolute left-0 right-0 top-[calc(100%+0.75rem)] z-30 overflow-hidden rounded-xl border border-slate-800 bg-slate-950 shadow-[0_20px_44px_rgba(2,8,23,0.7)]"
                    >
                        <div className="border-b border-slate-800 px-4 py-3">
                            <div className="flex items-center justify-between gap-4">
                                <div>
                                    <p className="text-sm font-semibold text-white">Search Results</p>
                                    <p className="text-xs text-slate-500">
                                        {isSearching
                                            ? "Searching..."
                                            : `${totalResultCount} result${totalResultCount === 1 ? "" : "s"}`}
                                    </p>
                                </div>
                                <span
                                    className="rounded-full border border-slate-800 bg-slate-900 px-2.5 py-1 text-[11px] font-medium text-slate-400">
                  {results.limit} max
                </span>
                            </div>
                        </div>

                        <div className="max-h-112 space-y-5 overflow-y-auto px-4 py-4">
                            {isSearching ? (
                                <div
                                    className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-5 text-sm text-slate-400">
                                    Searching for "{query.trim()}"...
                                </div>
                            ) : totalResultCount === 0 ? (
                                <div
                                    className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-5 text-sm text-slate-400">
                                    No matching instances, groups, or players were found.
                                </div>
                            ) : (
                                <>
                                    {results.servers.length > 0 ? (
                                        <section className="space-y-2">
                                            <div
                                                className="flex items-center gap-2 px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                                                <FiActivity className="h-4 w-4 text-primary"/>
                                                Instances
                                            </div>
                                            <div className="space-y-2">
                                                {results.servers.map((server) => (
                                                    <button
                                                        key={server.id}
                                                        type="button"
                                                        onClick={() => handleNavigate(`/servers/${server.id}`)}
                                                        className="flex w-full items-start justify-between gap-3 rounded-xl border border-slate-800 bg-slate-900/70 px-4 py-3 text-left transition-colors hover:border-primary/30 hover:bg-slate-900"
                                                    >
                                                        <div className="min-w-0">
                                                            <p className="truncate text-sm font-semibold text-white">
                                                                {server.displayName || server.podName || server.id}
                                                            </p>
                                                            <p className="mt-1 truncate text-xs text-slate-500">
                                                                {server.podName} | {server.group} | {server.state}
                                                            </p>
                                                        </div>
                                                        <span
                                                            className="rounded-full bg-slate-800 px-2.5 py-1 text-[11px] font-medium text-slate-300">
                              {server.type}
                            </span>
                                                    </button>
                                                ))}
                                            </div>
                                        </section>
                                    ) : null}

                                    {results.groups.length > 0 ? (
                                        <section className="space-y-2">
                                            <div
                                                className="flex items-center gap-2 px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                                                <FiLayers className="h-4 w-4 text-primary"/>
                                                Groups
                                            </div>
                                            <div className="space-y-2">
                                                {results.groups.map((group) => (
                                                    <button
                                                        key={group.id}
                                                        type="button"
                                                        onClick={() =>
                                                            handleNavigate(`/groups/${encodeURIComponent(group.id)}`)
                                                        }
                                                        className="flex w-full items-start justify-between gap-3 rounded-xl border border-slate-800 bg-slate-900/70 px-4 py-3 text-left transition-colors hover:border-primary/30 hover:bg-slate-900"
                                                    >
                                                        <div className="min-w-0">
                                                            <p className="truncate text-sm font-semibold text-white">
                                                                {group.id}
                                                            </p>
                                                            <p className="mt-1 text-xs text-slate-500">{group.type}</p>
                                                        </div>
                                                        <span
                                                            className={`rounded-full px-2.5 py-1 text-[11px] font-medium ${
                                                                group.maintenance
                                                                    ? "bg-amber-500/10 text-amber-300"
                                                                    : "bg-emerald-500/10 text-emerald-300"
                                                            }`}
                                                        >
                              {group.maintenance ? "Maintenance" : "Active"}
                            </span>
                                                    </button>
                                                ))}
                                            </div>
                                        </section>
                                    ) : null}

                                    {results.players.length > 0 ? (
                                        <section className="space-y-2">
                                            <div
                                                className="flex items-center gap-2 px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                                                <FiUsers className="h-4 w-4 text-primary"/>
                                                Players
                                            </div>
                                            <div className="space-y-2">
                                                {results.players.map((player) => (
                                                    <div
                                                        key={player.uuid}
                                                        className="flex items-center justify-between gap-3 rounded-xl border border-slate-800 bg-slate-900/70 px-4 py-3"
                                                    >
                                                        <div className="min-w-0">
                                                            <p className="truncate text-sm font-semibold text-white">
                                                                {player.name}
                                                            </p>
                                                            <p className="mt-1 truncate text-xs text-slate-500">
                                                                {player.permissionGroup} |{" "}
                                                                {player.online
                                                                    ? `Connected ${formatDateTime(player.connectedAt)}`
                                                                    : `First join ${formatDateTime(player.firstJoin)}`}
                                                            </p>
                                                        </div>
                                                        <button
                                                            type="button"
                                                            disabled={loadingPlayerUuid === player.uuid}
                                                            onClick={() => void handleOpenPlayer(player.uuid)}
                                                            className="app-button-field button-hover-lift button-shadow-neutral inline-flex shrink-0 items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                                                        >
                                                            {loadingPlayerUuid === player.uuid ? "Loading..." : "Manage"}
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        </section>
                                    ) : null}
                                </>
                            )}
                        </div>
                    </motion.div>
                ) : null}
            </div>

            <PlayerManagementModal
                player={selectedPlayer}
                onClose={() => setSelectedPlayer(null)}
            />
        </>
    );
};

export default HeaderSearch;
