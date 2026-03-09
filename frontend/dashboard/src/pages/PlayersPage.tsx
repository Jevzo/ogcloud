import {useCallback, useEffect, useState} from "react";
import {motion} from "motion/react";
import {FiChevronLeft, FiChevronRight, FiEdit2, FiUsers} from "react-icons/fi";

import AppToasts from "@/components/AppToasts";
import PlayerManagementModal from "@/components/PlayerManagementModal";
import TableRefreshButton from "@/components/TableRefreshButton";
import {listPersistedPlayers} from "@/lib/api";
import {formatDateTime} from "@/lib/server-display";
import {useAuthStore} from "@/store/auth-store";
import type {PaginatedResponse} from "@/types/dashboard";
import {getPaginatedHasNext, getPaginatedTotalPages} from "@/types/dashboard";
import type {PersistedPlayerRecord} from "@/types/player";

const PLAYER_PAGE_SIZE = 10;
const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_PLAYER_PAGE: PaginatedResponse<PersistedPlayerRecord> = {
    items: [],
    page: 0,
    size: PLAYER_PAGE_SIZE,
    totalItems: 0,
};

const getPlayerStatusTone = (online: boolean) =>
    online
        ? "bg-emerald-500/10 text-emerald-400"
        : "bg-slate-800 text-slate-300";

const formatPermissionExpiry = (endMillis: number) => {
    if (endMillis === -1) {
        return "Permanent";
    }

    if (!Number.isFinite(endMillis) || endMillis <= 0) {
        return "--";
    }

    return formatDateTime(String(endMillis));
};

const PlayersPage = () => {
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);

    const [playerPage, setPlayerPage] =
        useState<PaginatedResponse<PersistedPlayerRecord>>(EMPTY_PLAYER_PAGE);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [currentPage, setCurrentPage] = useState(0);
    const [selectedPlayer, setSelectedPlayer] = useState<PersistedPlayerRecord | null>(
        null
    );

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadPlayersPage = useCallback(async (showLoading = true) => {
        if (showLoading) {
            setIsLoading(true);
        }

        try {
            const accessToken = await getValidAccessToken();
            const nextPage = await listPersistedPlayers(accessToken, {
                page: currentPage,
                size: PLAYER_PAGE_SIZE,
            });

            setPlayerPage(nextPage);
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to load players."
            );
        } finally {
            setIsLoading(false);
        }
    }, [currentPage, getValidAccessToken]);

    useEffect(() => {
        let active = true;

        const runLoad = async (showLoading = true) => {
            if (!active) {
                return;
            }

            await loadPlayersPage(showLoading);
        };

        void runLoad(true);

        const intervalId = window.setInterval(() => {
            void runLoad(false);
        }, REFRESH_INTERVAL_MS);

        return () => {
            active = false;
            window.clearInterval(intervalId);
        };
    }, [loadPlayersPage]);

    const applyUpdatedPlayer = (updatedPlayer: PersistedPlayerRecord) => {
        setPlayerPage((currentValue) => ({
            ...currentValue,
            items: currentValue.items.map((player) =>
                player.uuid === updatedPlayer.uuid
                    ? {
                        ...player,
                        permission: updatedPlayer.permission,
                        online: updatedPlayer.online,
                        proxyId: updatedPlayer.proxyId,
                        serverId: updatedPlayer.serverId,
                        connectedAt: updatedPlayer.connectedAt,
                    }
                    : player
            ),
        }));
    };

    const totalPages = getPaginatedTotalPages(playerPage);

    return (
        <div className="space-y-8">
            <AppToasts
                items={
                    errorMessage
                        ? [
                            {
                                id: "players-error",
                                message: errorMessage,
                                onDismiss: () => setErrorMessage(null),
                                tone: "error" as const,
                            },
                        ]
                        : []
                }
            />

            <motion.section
                initial={{y: 12, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut"}}
                className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between"
            >
                <div>
                    <h2 className="flex items-center gap-2 text-lg font-bold text-white">
                        <FiUsers className="h-5 w-5 text-primary"/>
                        Players
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                        Review persisted player records, update permission groups, and move
                        active players between servers.
                    </p>
                </div>
            </motion.section>

            <motion.section
                initial={{y: 16, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut", delay: 0.05}}
                className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
            >
                <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <div className="flex items-center">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Persisted Players
                            </h3>
                        </div>
                        <div className="flex items-center justify-between gap-3 sm:justify-end">
              <span className="text-xs text-slate-500">
                {playerPage.totalItems} total player
                  {playerPage.totalItems === 1 ? "" : "s"}
              </span>
                            <TableRefreshButton
                                onClick={() => {
                                    void loadPlayersPage(false);
                                }}
                                isRefreshing={isLoading}
                                label="Refresh player table"
                            />
                        </div>
                    </div>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left">
                        <thead>
                        <tr className="border-b border-slate-800 bg-slate-800/30">
                            <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                Player
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                Permission Group
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                Status
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                First Join
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                UUID
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                Connected
                            </th>
                            <th className="px-6 py-4 text-right text-xs font-semibold uppercase text-slate-500">
                                Actions
                            </th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800">
                        {isLoading ? (
                            <tr>
                                <td
                                    colSpan={7}
                                    className="px-6 py-10 text-center text-sm text-slate-400"
                                >
                                    Loading players...
                                </td>
                            </tr>
                        ) : playerPage.items.length === 0 ? (
                            <tr>
                                <td
                                    colSpan={7}
                                    className="px-6 py-10 text-center text-sm text-slate-400"
                                >
                                    No persisted players were found.
                                </td>
                            </tr>
                        ) : (
                            playerPage.items.map((player) => (
                                <tr key={player.uuid} className="transition-colors hover:bg-slate-800/20">
                                    <td className="px-6 py-4">
                                        <p className="text-sm font-semibold text-white">{player.name}</p>
                                    </td>
                                    <td className="px-6 py-4">
                                        <div>
                                            <p className="text-sm font-medium text-slate-200">
                                                {player.permission.group}
                                            </p>
                                            <p className="text-xs text-slate-500">
                                                {formatPermissionExpiry(player.permission.endMillis)}
                                            </p>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4">
                      <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${getPlayerStatusTone(
                              player.online
                          )}`}
                      >
                        <span className="h-1 w-1 rounded-full bg-current"/>
                          {player.online ? "Online" : "Offline"}
                      </span>
                                    </td>
                                    <td className="px-6 py-4 text-sm text-slate-400">
                                        {formatDateTime(player.firstJoin)}
                                    </td>
                                    <td className="px-6 py-4 font-mono text-sm text-slate-300">
                                        {player.uuid}
                                    </td>
                                    <td className="px-6 py-4 text-sm text-slate-400">
                                        {formatDateTime(player.connectedAt)}
                                    </td>
                                    <td className="px-6 py-4 text-right">
                                        <button
                                            type="button"
                                            onClick={() => setSelectedPlayer(player)}
                                            disabled={selectedPlayer !== null}
                                            className="button-hover-lift button-shadow-primary inline-flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary disabled:cursor-not-allowed disabled:opacity-60"
                                            aria-label={`Manage ${player.name}`}
                                            title={`Manage ${player.name}`}
                                        >
                                            <FiEdit2 className="h-4 w-4"/>
                                        </button>
                                    </td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>

                <div
                    className="flex flex-col gap-3 border-t border-slate-800 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-sm text-slate-400">
            Page {playerPage.page + 1} of {totalPages}
          </span>
                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            disabled={currentPage === 0 || isLoading}
                            onClick={() => setCurrentPage((value) => Math.max(0, value - 1))}
                            className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                            <FiChevronLeft className="h-4 w-4"/>
                            Previous
                        </button>
                        <button
                            type="button"
                            disabled={!getPaginatedHasNext(playerPage) || isLoading}
                            onClick={() => setCurrentPage((value) => value + 1)}
                            className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                            Next
                            <FiChevronRight className="h-4 w-4"/>
                        </button>
                    </div>
                </div>
            </motion.section>

            <PlayerManagementModal
                player={selectedPlayer}
                onClose={() => setSelectedPlayer(null)}
                onPlayerUpdated={applyUpdatedPlayer}
                onTransferComplete={() => loadPlayersPage(false)}
            />
        </div>
    );
};

export default PlayersPage;
