import { useCallback, useEffect, useState } from "react";
import { motion } from "motion/react";
import { Link, useNavigate } from "react-router";
import { FiActivity, FiClock, FiGrid, FiLayers, FiServer, FiUsers } from "react-icons/fi";

import AppToasts from "@/components/AppToasts";
import TableRefreshButton from "@/components/TableRefreshButton";
import { API_LATENCY_WINDOW_MS, getAverageApiLatency } from "@/lib/api-latency";
import { getDashboardOverview } from "@/lib/api";
import { formatDateTime } from "@/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import type {
    DashboardOverviewGroup,
    DashboardOverviewResponse,
    DashboardOverviewScalingAction,
} from "@/types/dashboard";

const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_OVERVIEW: DashboardOverviewResponse = {
    stats: {
        totalPlayers: 0,
        maxPlayers: 0,
        activeServers: 0,
        maintenanceEnabled: false,
    },
    groups: [],
    scalingActions: [],
};

const getGroupModeTone = (mode: string) =>
    mode.toUpperCase() === "STATIC" ? "bg-amber-400 text-slate-950" : "bg-primary text-slate-950";

const GROUP_THUMBNAILS = [
    "/static/thumbnails/thumbnail-1.jpg",
    "/static/thumbnails/thumbnail-2.jpg",
    "/static/thumbnails/thumbnail-3.jpg",
    "/static/thumbnails/thumbnail-4.jpg",
    "/static/thumbnails/thumbnail-5.jpg",
    "/static/thumbnails/thumbnail-6.jpg",
] as const;

const getRandomGroupThumbnail = () =>
    GROUP_THUMBNAILS[Math.floor(Math.random() * GROUP_THUMBNAILS.length)];

const DashboardHome = () => {
    const navigate = useNavigate();
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);

    const [overview, setOverview] = useState<DashboardOverviewResponse>(EMPTY_OVERVIEW);
    const [averageLatencyMs, setAverageLatencyMs] = useState<number | null>(getAverageApiLatency());
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [groupThumbnailMap, setGroupThumbnailMap] = useState<Record<string, string>>({});

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadOverview = useCallback(
        async (showLoading = false) => {
            if (showLoading) {
                setIsLoading(true);
            }

            try {
                const accessToken = await getValidAccessToken();
                const result = await getDashboardOverview(accessToken);
                setOverview(result.data);
                setAverageLatencyMs(getAverageApiLatency());
                setErrorMessage(null);
            } catch (error) {
                setAverageLatencyMs(getAverageApiLatency());
                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to load dashboard overview.",
                );
            } finally {
                setIsLoading(false);
            }
        },
        [getValidAccessToken],
    );

    useEffect(() => {
        let active = true;

        const runLoad = async (showLoading = false) => {
            if (!active) {
                return;
            }

            await loadOverview(showLoading);
        };

        void runLoad(true);

        const intervalId = window.setInterval(() => {
            void runLoad(false);
        }, REFRESH_INTERVAL_MS);

        return () => {
            active = false;
            window.clearInterval(intervalId);
        };
    }, [loadOverview]);

    useEffect(() => {
        if (overview.groups.length === 0) {
            return;
        }

        setGroupThumbnailMap((currentMap) => {
            const nextMap = { ...currentMap };
            let didChange = false;

            for (const group of overview.groups) {
                if (!nextMap[group.name]) {
                    nextMap[group.name] = getRandomGroupThumbnail();
                    didChange = true;
                }
            }

            return didChange ? nextMap : currentMap;
        });
    }, [overview.groups]);

    const playerLoadPercent =
        overview.stats.maxPlayers > 0
            ? Math.min(
                  100,
                  Math.round((overview.stats.totalPlayers / overview.stats.maxPlayers) * 100),
              )
            : 0;

    const statsCards = [
        {
            title: "Total Players",
            value: `${overview.stats.totalPlayers}`,
            suffix: overview.stats.maxPlayers > 0 ? `/ ${overview.stats.maxPlayers}` : null,
            tone: "text-emerald-400",
            meta: `${playerLoadPercent}% of network capacity`,
            icon: FiUsers,
        },
        {
            title: "Active Servers",
            value: `${overview.stats.activeServers}`,
            suffix: "Running",
            tone: "text-primary",
            meta: "Visible active instances",
            icon: FiServer,
        },
        {
            title: "API Latency (10m Avg)",
            value: averageLatencyMs !== null ? `${averageLatencyMs}` : "--",
            suffix: "ms",
            tone: "text-emerald-400",
            meta: `Rolling average over ${Math.round(API_LATENCY_WINDOW_MS / 60000)} minutes`,
            icon: FiClock,
        },
        {
            title: "Maintenance",
            value: overview.stats.maintenanceEnabled ? "Enabled" : "Disabled",
            suffix: null,
            tone: overview.stats.maintenanceEnabled ? "text-amber-300" : "text-slate-400",
            meta: overview.stats.maintenanceEnabled
                ? "Player joins are restricted"
                : "Network open for players",
            icon: FiActivity,
        },
    ] as const;
    const scalingActions = overview.scalingActions ?? [];

    return (
        <div className="space-y-8">
            <AppToasts
                items={[
                    ...(errorMessage
                        ? [
                              {
                                  id: "dashboard-error",
                                  message: errorMessage,
                                  onDismiss: () => setErrorMessage(null),
                                  tone: "error" as const,
                              },
                          ]
                        : []),
                ]}
            />

            <motion.section
                initial={{ y: 12, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut" }}
            >
                <div className="mb-4 flex items-center justify-between">
                    <h2 className="flex items-center gap-2 text-lg font-bold text-white">
                        <FiActivity className="h-5 w-5 text-primary" />
                        Status Overview
                    </h2>
                    <span className="text-xs text-slate-500">Auto-refreshing every 10s</span>
                </div>

                <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
                    {statsCards.map(({ title, value, suffix, tone, meta, icon: Icon }) => (
                        <div
                            key={title}
                            className="rounded-xl border border-slate-800 bg-slate-900 p-6 shadow-sm"
                        >
                            <div className="mb-4 flex items-start justify-between">
                                <p className="text-sm font-medium text-slate-400">{title}</p>
                                <Icon className={`h-5 w-5 ${tone}`} />
                            </div>
                            <div>
                                <h3 className="text-3xl font-bold tracking-tight text-white">
                                    {isLoading ? "--" : value}{" "}
                                    {suffix && (
                                        <span className="text-lg font-medium text-slate-500">
                                            {suffix}
                                        </span>
                                    )}
                                </h3>
                                <p
                                    className={`mt-2 text-xs font-medium uppercase tracking-wide ${tone}`}
                                >
                                    {meta}
                                </p>
                            </div>
                        </div>
                    ))}
                </div>
            </motion.section>

            <motion.section
                initial={{ y: 14, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
            >
                <div className="mb-4 flex items-center justify-between">
                    <h2 className="flex items-center gap-2 text-lg font-bold text-white">
                        <FiGrid className="h-5 w-5 text-primary" />
                        Server Groups
                    </h2>
                    <button
                        type="button"
                        onClick={() => navigate("/groups")}
                        className="text-sm font-medium text-primary transition-colors hover:underline"
                    >
                        View all groups
                    </button>
                </div>

                <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
                    {overview.groups.length === 0 && !isLoading ? (
                        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400 lg:col-span-3">
                            No visible groups right now.
                        </div>
                    ) : (
                        overview.groups.map((group: DashboardOverviewGroup) => (
                            <Link
                                key={group.name}
                                to={`/groups/${encodeURIComponent(group.name)}`}
                                className="group overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm transition-colors hover:border-primary/30"
                            >
                                <div className="relative h-28 overflow-hidden bg-slate-800 sm:h-28">
                                    <img
                                        src={
                                            groupThumbnailMap[group.name] ??
                                            getRandomGroupThumbnail()
                                        }
                                        alt=""
                                        className="h-full w-full object-cover opacity-65 transition-transform duration-300 group-hover:scale-105"
                                    />
                                    <div className="absolute inset-0 bg-linear-to-b from-slate-950/0 via-slate-950/10 via-35% to-slate-900" />
                                    <div className="absolute left-4 top-4">
                                        <span
                                            className={`rounded px-2 py-0.5 text-[10px] font-bold uppercase ${getGroupModeTone(
                                                group.mode,
                                            )}`}
                                        >
                                            {group.mode}
                                        </span>
                                    </div>
                                    <div className="absolute inset-x-4 bottom-2">
                                        <h4 className="text-base font-bold text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.55)]">
                                            {group.name}
                                        </h4>
                                    </div>
                                </div>
                                <div className="space-y-2 p-3">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-500">Instances</span>
                                        <span className="font-medium text-slate-200">
                                            {group.activeInstances} Active
                                        </span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-500">Player Load</span>
                                        <span className="font-medium text-slate-200">
                                            {group.players} players
                                        </span>
                                    </div>
                                    <div className="pt-2">
                                        <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-800">
                                            <div
                                                className="h-full bg-primary"
                                                style={{
                                                    width: `${Math.min(100, group.capacityPercent)}%`,
                                                }}
                                            />
                                        </div>
                                    </div>
                                </div>
                            </Link>
                        ))
                    )}
                </div>
            </motion.section>

            <motion.section
                initial={{ y: 16, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut", delay: 0.09 }}
                className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
            >
                <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <h2 className="flex items-center gap-2 text-lg font-bold text-white">
                            <FiLayers className="h-5 w-5 text-primary" />
                            Recent Scaling Actions
                        </h2>
                        <div className="flex items-center justify-between gap-3 sm:justify-end">
                            <span className="text-xs text-slate-500">
                                {scalingActions.length} action
                                {scalingActions.length === 1 ? "" : "s"}
                            </span>
                            <TableRefreshButton
                                onClick={() => {
                                    void loadOverview(false);
                                }}
                                isRefreshing={isLoading}
                                label="Refresh scaling actions"
                            />
                        </div>
                    </div>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left">
                        <thead>
                            <tr className="border-b border-slate-800 bg-slate-800/30">
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Group
                                </th>
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Action
                                </th>
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Reason
                                </th>
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Server
                                </th>
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Timestamp
                                </th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800">
                            {scalingActions.length === 0 && !isLoading ? (
                                <tr>
                                    <td
                                        colSpan={5}
                                        className="px-6 py-10 text-center text-sm text-slate-400"
                                    >
                                        No scaling actions logged yet.
                                    </td>
                                </tr>
                            ) : (
                                scalingActions.map((action: DashboardOverviewScalingAction) => {
                                    return (
                                        <tr
                                            key={
                                                action.id ??
                                                `${action.groupId}:${action.action}:${action.timestamp}`
                                            }
                                            className="transition-colors hover:bg-slate-800/30"
                                        >
                                            <td className="px-6 py-4">
                                                <span className="text-sm font-semibold text-slate-200">
                                                    {action.groupId}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 text-sm text-slate-300">
                                                {action.action}
                                            </td>
                                            <td className="px-6 py-4 text-sm text-slate-300">
                                                {action.reason}
                                            </td>
                                            <td className="px-6 py-4 text-sm text-slate-300">
                                                {action.serverId ?? "--"}
                                            </td>
                                            <td className="px-6 py-4 text-sm text-slate-400">
                                                {formatDateTime(action.timestamp)}
                                            </td>
                                        </tr>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>
            </motion.section>
        </div>
    );
};

export default DashboardHome;
