import { useCallback, useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router";
import { motion } from "motion/react";
import { FiGlobe } from "react-icons/fi";

import AppToasts from "@/components/AppToasts";
import { RUNTIME_REFRESH_OPTIONS } from "@/lib/group-runtime";
import {
    getNetworkLocks,
    getNetworkSettings,
    getNetworkStatus,
    listGroups,
    requestNetworkRestart,
    requestRuntimeRefresh,
    toggleNetworkMaintenance,
    updateNetworkSettings,
} from "@/lib/api";
import { hasAdminAccess } from "@/lib/roles";
import type { NetworkPageContextValue } from "@/pages/network/context";
import { useAuthStore } from "@/store/auth-store";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import type { GroupListItem } from "@/types/dashboard";
import type {
    NetworkLockRecord,
    NetworkSettingsRecord,
    NetworkStatusRecord,
    UpdateNetworkPayload,
} from "@/types/network";
import type { RuntimeBundleScope } from "@/types/runtime";

const DEFAULT_STATUS: NetworkStatusRecord = {
    onlinePlayers: 0,
    serverCount: 0,
    proxyCount: 0,
};
const REFRESH_INTERVAL_MS = 10_000;
const NETWORK_SECTION_ITEMS = [
    {
        label: "Overview",
        to: "overview",
        description: "Status, previews, and live network state.",
    },
    {
        label: "Server Settings",
        to: "server-settings",
        description: "Maintenance, restart flow, locks, and capacity.",
    },
    {
        label: "General",
        to: "general",
        description: "Routing and plugin-wide behavior.",
    },
    {
        label: "Messaging",
        to: "messaging",
        description: "MOTD, version name, kick text, and tablist copy.",
    },
] as const;

const NetworkPage = () => {
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const userRole = useAuthStore((state) => state.session?.user.role);
    const setGeneralSettings = useNetworkSettingsStore((state) => state.setGeneral);

    const [settings, setSettings] = useState<NetworkSettingsRecord | null>(null);
    const [status, setStatus] = useState<NetworkStatusRecord>(DEFAULT_STATUS);
    const [groups, setGroups] = useState<GroupListItem[]>([]);
    const [locks, setLocks] = useState<NetworkLockRecord[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isTogglingMaintenance, setIsTogglingMaintenance] = useState(false);
    const [isRestartingNetwork, setIsRestartingNetwork] = useState(false);
    const [refreshingScope, setRefreshingScope] = useState<RuntimeBundleScope | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);
    const isAdmin = hasAdminAccess(userRole);

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadAllNetworkData = useCallback(
        async (showLoading = true) => {
            if (showLoading) {
                setIsLoading(true);
            }

            try {
                const accessToken = await getValidAccessToken();
                const [nextSettings, nextStatus, nextGroups, nextLocks] = await Promise.all([
                    getNetworkSettings(accessToken),
                    getNetworkStatus(accessToken),
                    listGroups(accessToken),
                    getNetworkLocks(accessToken),
                ]);

                setSettings(nextSettings);
                setStatus(nextStatus);
                setGroups(nextGroups);
                setLocks(nextLocks);
                setGeneralSettings(nextSettings.general);
                setErrorMessage(null);
            } catch (error) {
                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to load network settings.",
                );
            } finally {
                if (showLoading) {
                    setIsLoading(false);
                }
            }
        },
        [getValidAccessToken, setGeneralSettings],
    );

    const refreshNetworkTelemetry = useCallback(
        async (reportErrors = false) => {
            try {
                const accessToken = await getValidAccessToken();
                const [nextStatus, nextLocks] = await Promise.all([
                    getNetworkStatus(accessToken),
                    getNetworkLocks(accessToken),
                ]);

                setStatus(nextStatus);
                setLocks(nextLocks);

                if (reportErrors) {
                    setErrorMessage(null);
                }
            } catch (error) {
                if (!reportErrors) {
                    return;
                }

                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to refresh network state.",
                );
            }
        },
        [getValidAccessToken],
    );

    useEffect(() => {
        void loadAllNetworkData(true);

        const intervalId = window.setInterval(() => {
            void refreshNetworkTelemetry(false);
        }, REFRESH_INTERVAL_MS);

        return () => {
            window.clearInterval(intervalId);
        };
    }, [loadAllNetworkData, refreshNetworkTelemetry]);

    useEffect(() => {
        if (!successMessage) {
            return;
        }

        const timeoutId = window.setTimeout(() => {
            setSuccessMessage(null);
        }, 3000);

        return () => {
            window.clearTimeout(timeoutId);
        };
    }, [successMessage]);

    const saveSettings = useCallback(
        async (payload: UpdateNetworkPayload, nextSuccessMessage: string) => {
            try {
                const accessToken = await getValidAccessToken();
                const nextSettings = await updateNetworkSettings(accessToken, payload);

                setSettings(nextSettings);
                setGeneralSettings(nextSettings.general);
                setErrorMessage(null);
                setSuccessMessage(nextSuccessMessage);

                return nextSettings;
            } catch (error) {
                const nextError =
                    error instanceof Error
                        ? error
                        : new Error("Unable to save network settings.");

                setErrorMessage(nextError.message);
                throw nextError;
            }
        },
        [getValidAccessToken, setGeneralSettings],
    );

    const setMaintenance = useCallback(
        async (enabled: boolean) => {
            setIsTogglingMaintenance(true);

            try {
                const accessToken = await getValidAccessToken();
                const nextSettings = await toggleNetworkMaintenance(accessToken, enabled);

                setSettings(nextSettings);
                setGeneralSettings(nextSettings.general);
                setErrorMessage(null);
                setSuccessMessage(
                    enabled
                        ? "Network maintenance has been enabled."
                        : "Network maintenance has been disabled.",
                );

                return nextSettings;
            } catch (error) {
                const nextError =
                    error instanceof Error
                        ? error
                        : new Error("Unable to update network maintenance.");

                setErrorMessage(nextError.message);
                throw nextError;
            } finally {
                setIsTogglingMaintenance(false);
            }
        },
        [getValidAccessToken, setGeneralSettings],
    );

    const requestNetworkRestartAction = useCallback(async () => {
        setIsRestartingNetwork(true);

        try {
            const accessToken = await getValidAccessToken();

            await requestNetworkRestart(accessToken);
            await refreshNetworkTelemetry(false);
            setErrorMessage(null);
            setSuccessMessage("Full network restart requested.");
        } catch (error) {
            const nextError =
                error instanceof Error
                    ? error
                    : new Error("Unable to request a network restart.");

            setErrorMessage(nextError.message);
            throw nextError;
        } finally {
            setIsRestartingNetwork(false);
        }
    }, [getValidAccessToken, refreshNetworkTelemetry]);

    const requestRuntimeRefreshAction = useCallback(
        async (scope: RuntimeBundleScope) => {
            setRefreshingScope(scope);

            try {
                const accessToken = await getValidAccessToken();

                await requestRuntimeRefresh(accessToken, scope);
                setErrorMessage(null);
                setSuccessMessage(
                    `${RUNTIME_REFRESH_OPTIONS.find((option) => option.scope === scope)?.label ?? scope} refresh requested.`,
                );
            } catch (error) {
                const nextError =
                    error instanceof Error
                        ? error
                        : new Error("Unable to request runtime refresh.");

                setErrorMessage(nextError.message);
                throw nextError;
            } finally {
                setRefreshingScope(null);
            }
        },
        [getValidAccessToken],
    );

    const contextValue: NetworkPageContextValue = {
        settings,
        status,
        groups,
        locks,
        isLoading,
        isAdmin,
        isRestartingNetwork,
        isTogglingMaintenance,
        refreshingScope,
        showErrorMessage: (message: string) => {
            setSuccessMessage(null);
            setErrorMessage(message);
        },
        saveSettings,
        setMaintenance,
        requestNetworkRestart: requestNetworkRestartAction,
        requestRuntimeRefresh: requestRuntimeRefreshAction,
    };

    return (
        <div className="space-y-6">
            <AppToasts
                items={[
                    ...(errorMessage
                        ? [
                              {
                                  id: "network-error",
                                  message: errorMessage,
                                  onDismiss: () => setErrorMessage(null),
                                  tone: "error" as const,
                              },
                          ]
                        : []),
                    ...(successMessage
                        ? [
                              {
                                  id: "network-success",
                                  message: successMessage,
                                  onDismiss: () => setSuccessMessage(null),
                                  tone: "success" as const,
                              },
                          ]
                        : []),
                ]}
            />

            <motion.section
                initial={{ y: 12, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut" }}
                className="flex flex-col gap-3"
            >
                <div>
                    <h2 className="flex items-center gap-2 text-lg font-bold text-white">
                        <FiGlobe className="h-5 w-5 text-primary" />
                        Network
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                        Manage global routing, maintenance flow, player-facing copy, and network
                        operations from focused subpages.
                    </p>
                </div>
            </motion.section>

            <motion.section
                initial={{ y: -14, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.3, ease: "easeOut" }}
                className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/80 shadow-sm"
            >
                <div className="border-b border-slate-800/80 px-5 py-4">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-primary/80">
                        Network Sections
                    </p>
                    <p className="mt-1 text-sm text-slate-400">
                        These pages sit under the Network area and stay visible while you move
                        between network controls.
                    </p>
                </div>
                <div className="px-5 py-4">
                    <div className="border-l border-primary/20 pl-4">
                        <div className="flex flex-wrap gap-2">
                            {NETWORK_SECTION_ITEMS.map(({ label, to, description }) => (
                                <NavLink
                                    key={to}
                                    to={to}
                                    className={({ isActive }) =>
                                        `group min-w-52 rounded-xl border px-4 py-3 transition-all ${
                                            isActive
                                                ? "border-primary/35 bg-primary/10 text-primary shadow-[0_18px_40px_rgba(34,197,94,0.08)]"
                                                : "border-slate-800 bg-slate-950/50 text-slate-300 hover:border-slate-700 hover:bg-slate-900 hover:text-white"
                                        }`
                                    }
                                >
                                    <p className="text-sm font-semibold">{label}</p>
                                    <p className="mt-1 text-xs leading-5 text-slate-500 transition-colors group-hover:text-slate-400">
                                        {description}
                                    </p>
                                </NavLink>
                            ))}
                        </div>
                    </div>
                </div>
            </motion.section>

            <Outlet context={contextValue} />
        </div>
    );
};

export default NetworkPage;
