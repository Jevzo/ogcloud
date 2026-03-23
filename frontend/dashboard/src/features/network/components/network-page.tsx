import { useCallback, useEffect, useState } from "react";
import { Clock3Icon, LoaderCircleIcon } from "lucide-react";
import { toast } from "sonner";

import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { hasAdminAccess } from "@/features/auth/lib/roles";
import NetworkGeneralPage from "@/features/network/components/network-general-page";
import NetworkMessagingPage from "@/features/network/components/network-messaging-page";
import NetworkServerSettingsPage from "@/features/network/components/network-server-settings-page";
import {
    NetworkPageContextProvider,
    type NetworkPageContextValue,
} from "@/features/network/lib/context";
import {
    getNetworkLocks,
    getNetworkSettings,
    getNetworkStatus,
    listGroups,
    requestNetworkRestart,
    toggleNetworkMaintenance,
    updateNetworkSettings,
} from "@/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatDateTime } from "@/features/servers/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import type { GroupListItem } from "@/types/dashboard";
import type {
    NetworkLockRecord,
    NetworkSettingsRecord,
    NetworkStatusRecord,
    UpdateNetworkPayload,
} from "@/types/network";

const DEFAULT_STATUS: NetworkStatusRecord = {
    onlinePlayers: 0,
    serverCount: 0,
    proxyCount: 0,
};

const REFRESH_INTERVAL_MS = 10_000;

const NETWORK_SECTION_ITEMS = [
    {
        value: "general",
        label: "General",
    },
    {
        value: "server-settings",
        label: "Server Settings",
    },
    {
        value: "messaging",
        label: "Messaging",
    },
] as const;

const SummaryCard = ({
    helper,
    label,
    value,
}: {
    helper: string;
    label: string;
    value: string;
}) => (
    <Card className="border border-border/70 bg-card/85 shadow-none">
        <CardHeader className="pb-3">
            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                {label}
            </CardDescription>
            <CardTitle className="text-xl tracking-tight">{value}</CardTitle>
        </CardHeader>
        <CardContent className="pt-0 text-sm text-muted-foreground">{helper}</CardContent>
    </Card>
);

const LastSyncSurface = ({
    isRefreshing,
    lastUpdatedAt,
}: {
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
}) => (
    <div className="flex min-h-10 items-center gap-2 rounded-lg border border-border/70 bg-card/70 px-3 text-sm text-muted-foreground">
        {isRefreshing ? (
            <LoaderCircleIcon className="size-4 animate-spin text-primary" />
        ) : (
            <Clock3Icon className="size-4 text-primary" />
        )}
        <span>
            {lastUpdatedAt
                ? `Last sync ${formatDateTime(new Date(lastUpdatedAt).toISOString())}`
                : "Waiting for first sync"}
        </span>
    </div>
);

const NetworkPageSkeleton = () => (
    <div className="space-y-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div className="space-y-3">
                <Skeleton className="h-9 w-40" />
                <Skeleton className="h-4 w-96" />
            </div>
            <Skeleton className="h-10 w-48" />
        </div>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
                <Card
                    key={`network-summary-skeleton-${index}`}
                    className="border border-border/70 bg-card/85"
                >
                    <CardHeader>
                        <Skeleton className="h-4 w-24" />
                        <Skeleton className="h-8 w-24" />
                    </CardHeader>
                    <CardContent>
                        <Skeleton className="h-4 w-40" />
                    </CardContent>
                </Card>
            ))}
        </div>
        <Skeleton className="h-8 w-80 max-w-full" />
    </div>
);

const NetworkPage = () => {
    const getAccessToken = useAccessToken();
    const userRole = useAuthStore((state) => state.session?.user.role);
    const setGeneralSettings = useNetworkSettingsStore((state) => state.setGeneral);

    const [settings, setSettings] = useState<NetworkSettingsRecord | null>(null);
    const [status, setStatus] = useState<NetworkStatusRecord>(DEFAULT_STATUS);
    const [groups, setGroups] = useState<GroupListItem[]>([]);
    const [locks, setLocks] = useState<NetworkLockRecord[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [isTogglingMaintenance, setIsTogglingMaintenance] = useState(false);
    const [isRestartingNetwork, setIsRestartingNetwork] = useState(false);
    const [pageErrorMessage, setPageErrorMessage] = useState<string | null>(null);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);
    const isAdmin = hasAdminAccess(userRole);

    const loadAllNetworkData = useCallback(
        async (showLoading: boolean) => {
            if (showLoading) {
                setIsLoading(true);
            } else {
                setIsRefreshing(true);
            }

            try {
                const accessToken = await getAccessToken();
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
                setPageErrorMessage(null);
                setLastUpdatedAt(Date.now());
            } catch (error) {
                setPageErrorMessage(
                    error instanceof Error ? error.message : "Unable to load network settings.",
                );
            } finally {
                if (showLoading) {
                    setIsLoading(false);
                } else {
                    setIsRefreshing(false);
                }
            }
        },
        [getAccessToken, setGeneralSettings],
    );

    const refreshNetworkTelemetry = useCallback(
        async (reportErrors: boolean) => {
            try {
                const accessToken = await getAccessToken();
                const [nextStatus, nextLocks] = await Promise.all([
                    getNetworkStatus(accessToken),
                    getNetworkLocks(accessToken),
                ]);

                setStatus(nextStatus);
                setLocks(nextLocks);
                setLastUpdatedAt(Date.now());

                if (reportErrors) {
                    setPageErrorMessage(null);
                }
            } catch (error) {
                if (!reportErrors) {
                    return;
                }

                setPageErrorMessage(
                    error instanceof Error ? error.message : "Unable to refresh network state.",
                );
            }
        },
        [getAccessToken],
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

    const saveSettings = useCallback(
        async (payload: UpdateNetworkPayload, successMessage: string) => {
            try {
                const accessToken = await getAccessToken();
                const nextSettings = await updateNetworkSettings(accessToken, payload);

                setSettings(nextSettings);
                setGeneralSettings(nextSettings.general);
                setPageErrorMessage(null);
                toast.success(successMessage);

                return nextSettings;
            } catch (error) {
                throw error instanceof Error
                    ? error
                    : new Error("Unable to save network settings.");
            }
        },
        [getAccessToken, setGeneralSettings],
    );

    const setMaintenance = useCallback(
        async (enabled: boolean) => {
            setIsTogglingMaintenance(true);

            try {
                const accessToken = await getAccessToken();
                const nextSettings = await toggleNetworkMaintenance(accessToken, enabled);

                setSettings(nextSettings);
                setGeneralSettings(nextSettings.general);
                setPageErrorMessage(null);
                toast.success(
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

                toast.error(nextError.message);
                throw nextError;
            } finally {
                setIsTogglingMaintenance(false);
            }
        },
        [getAccessToken, setGeneralSettings],
    );

    const requestNetworkRestartAction = useCallback(async () => {
        setIsRestartingNetwork(true);

        try {
            const accessToken = await getAccessToken();

            await requestNetworkRestart(accessToken);
            await refreshNetworkTelemetry(false);
            setPageErrorMessage(null);
            toast.success("Full network restart requested.");
        } catch (error) {
            const nextError =
                error instanceof Error ? error : new Error("Unable to request a network restart.");

            toast.error(nextError.message);
            throw nextError;
        } finally {
            setIsRestartingNetwork(false);
        }
    }, [getAccessToken, refreshNetworkTelemetry]);

    const contextValue: NetworkPageContextValue = {
        settings,
        status,
        groups,
        locks,
        isAdmin,
        isLoading,
        isRefreshing,
        isRestartingNetwork,
        isTogglingMaintenance,
        saveSettings,
        setMaintenance,
        requestNetworkRestart: requestNetworkRestartAction,
    };

    const hasFreshData = lastUpdatedAt !== null;

    if (isLoading && !hasFreshData) {
        return <NetworkPageSkeleton />;
    }

    if (pageErrorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Network Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load the network workspace
                    </CardTitle>
                    <CardDescription className="text-sm text-destructive/80">
                        {pageErrorMessage}
                    </CardDescription>
                </CardHeader>
            </Card>
        );
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight text-foreground">
                        Network control
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Maintenance mode, default routing, and player-facing messaging for the
                        entire network.
                    </p>
                </div>

                <LastSyncSurface isRefreshing={isRefreshing} lastUpdatedAt={lastUpdatedAt} />
            </div>

            {pageErrorMessage && hasFreshData ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Showing the latest successful network snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {pageErrorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Online players"
                    value={`${status.onlinePlayers.toLocaleString()} / ${settings?.maxPlayers?.toLocaleString() ?? "--"}`}
                    helper="Current network occupancy against the configured global player cap."
                />
                <SummaryCard
                    label="Maintenance"
                    value={settings?.maintenance ? "Enabled" : "Disabled"}
                    helper="Current maintenance mode status applied across the network."
                />
                <SummaryCard
                    label="Game servers"
                    value={status.serverCount.toLocaleString()}
                    helper="Registered non-proxy runtime instances currently in the cluster."
                />
                <SummaryCard
                    label="Proxies"
                    value={status.proxyCount.toLocaleString()}
                    helper="Gateway nodes currently routing traffic into the network."
                />
            </div>

            <NetworkPageContextProvider value={contextValue}>
                <Tabs defaultValue="general" className="gap-4">
                    <TabsList variant="line" className="flex-wrap justify-start">
                        {NETWORK_SECTION_ITEMS.map(({ label, value }) => (
                            <TabsTrigger key={value} value={value}>
                                {label}
                            </TabsTrigger>
                        ))}
                    </TabsList>

                    <TabsContent value="general">
                        <NetworkGeneralPage />
                    </TabsContent>
                    <TabsContent value="server-settings">
                        <NetworkServerSettingsPage />
                    </TabsContent>
                    <TabsContent value="messaging">
                        <NetworkMessagingPage />
                    </TabsContent>
                </Tabs>
            </NetworkPageContextProvider>
        </div>
    );
};

export default NetworkPage;
