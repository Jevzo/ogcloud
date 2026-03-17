import { useCallback, useEffect, useMemo, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router";
import {
    ActivityIcon,
    GlobeIcon,
    LayoutGridIcon,
    MessageSquareTextIcon,
    RefreshCwIcon,
    Settings2Icon,
    ShieldCheckIcon,
    UsersIcon,
} from "lucide-react";
import { toast } from "sonner";

import { useAccessToken } from "@/hooks/use-access-token";
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
import { RUNTIME_REFRESH_OPTIONS } from "@/lib/group-runtime";
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
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

const DEFAULT_STATUS: NetworkStatusRecord = {
    onlinePlayers: 0,
    serverCount: 0,
    proxyCount: 0,
};

const REFRESH_INTERVAL_MS = 10_000;

const NETWORK_SECTION_ITEMS = [
    {
        value: "overview",
        label: "Overview",
        description: "Live status, current values, messaging previews, and network-wide health.",
        icon: LayoutGridIcon,
    },
    {
        value: "server-settings",
        label: "Server Settings",
        description: "Capacity, maintenance flow, runtime refreshes, restart confirmation, and locks.",
        icon: ActivityIcon,
    },
    {
        value: "general",
        label: "General",
        description: "Proxy routing, tablist enablement, permission synchronization, and presets.",
        icon: Settings2Icon,
    },
    {
        value: "messaging",
        label: "Messaging",
        description: "Version names, MOTD copy, kick text, and tablist content with live previews.",
        icon: MessageSquareTextIcon,
    },
] as const;

type NetworkSectionValue = (typeof NETWORK_SECTION_ITEMS)[number]["value"];

const getActiveSection = (pathname: string): NetworkSectionValue => {
    const section = pathname.split("/").filter(Boolean)[1];
    return NETWORK_SECTION_ITEMS.some((item) => item.value === section)
        ? (section as NetworkSectionValue)
        : "overview";
};

const NetworkPage = () => {
    const location = useLocation();
    const navigate = useNavigate();
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
    const [refreshingScope, setRefreshingScope] = useState<RuntimeBundleScope | null>(null);
    const [pageErrorMessage, setPageErrorMessage] = useState<string | null>(null);
    const isAdmin = hasAdminAccess(userRole);

    const activeSection = useMemo(
        () => getActiveSection(location.pathname),
        [location.pathname],
    );
    const activeSectionItem =
        NETWORK_SECTION_ITEMS.find((item) => item.value === activeSection) ?? NETWORK_SECTION_ITEMS[0];

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

    const handleReload = useCallback(async () => {
        await loadAllNetworkData(false);
    }, [loadAllNetworkData]);

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
                error instanceof Error
                    ? error
                    : new Error("Unable to request a network restart.");

            toast.error(nextError.message);
            throw nextError;
        } finally {
            setIsRestartingNetwork(false);
        }
    }, [getAccessToken, refreshNetworkTelemetry]);

    const requestRuntimeRefreshAction = useCallback(
        async (scope: RuntimeBundleScope) => {
            setRefreshingScope(scope);

            try {
                const accessToken = await getAccessToken();

                await requestRuntimeRefresh(accessToken, scope);
                const label =
                    RUNTIME_REFRESH_OPTIONS.find((option) => option.scope === scope)?.label ?? scope;
                toast.success(`${label} refresh requested.`);
            } catch (error) {
                const nextError =
                    error instanceof Error
                        ? error
                        : new Error("Unable to request runtime refresh.");

                toast.error(nextError.message);
                throw nextError;
            } finally {
                setRefreshingScope(null);
            }
        },
        [getAccessToken],
    );

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
        refreshingScope,
        reloadData: handleReload,
        saveSettings,
        setMaintenance,
        requestNetworkRestart: requestNetworkRestartAction,
        requestRuntimeRefresh: requestRuntimeRefreshAction,
    };

    return (
        <div className="space-y-6">
            <Card className="border-border/70 bg-card/80 shadow-sm">
                <CardHeader className="gap-5 border-b border-border/70">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div className="space-y-3">
                            <div className="flex flex-wrap items-center gap-2">
                                <Badge variant="outline" className="border-primary/30 text-primary">
                                    Network Shell
                                </Badge>
                                <Badge
                                    variant="outline"
                                    className={
                                        settings?.maintenance
                                            ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                            : "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                    }
                                >
                                    {settings?.maintenance ? "Maintenance enabled" : "Network open"}
                                </Badge>
                            </div>
                            <div className="space-y-1.5">
                                <CardTitle className="flex items-center gap-2 text-2xl tracking-tight">
                                    <GlobeIcon className="size-5 text-primary" />
                                    Network management
                                </CardTitle>
                                <CardDescription className="max-w-3xl text-sm leading-6">
                                    Preserve the nested network routing model while consolidating
                                    live status, network-wide forms, restart flow, and player-facing
                                    messaging into one shadcn-based subtree.
                                </CardDescription>
                            </div>
                        </div>

                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => void handleReload()}
                            disabled={isLoading || isRefreshing}
                        >
                            <RefreshCwIcon
                                className={`size-4 ${isRefreshing ? "animate-spin" : ""}`}
                            />
                            {isRefreshing ? "Refreshing" : "Refresh network"}
                        </Button>
                    </div>

                    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                        <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Players
                            </div>
                            <div className="mt-1 flex items-center gap-2 text-sm font-semibold text-foreground">
                                <UsersIcon className="size-4 text-primary" />
                                {status.onlinePlayers.toLocaleString()}
                            </div>
                        </div>
                        <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Capacity
                            </div>
                            <div className="mt-1 text-sm font-semibold text-foreground">
                                {settings?.maxPlayers?.toLocaleString() ?? "--"} max slots
                            </div>
                        </div>
                        <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Default group
                            </div>
                            <div className="mt-1 text-sm font-semibold text-foreground">
                                {settings?.defaultGroup ?? "--"}
                            </div>
                        </div>
                        <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Permissions
                            </div>
                            <div className="mt-1 flex items-center gap-2 text-sm font-semibold text-foreground">
                                <ShieldCheckIcon className="size-4 text-primary" />
                                {settings?.general.permissionSystemEnabled ? "Enabled" : "Disabled"}
                            </div>
                        </div>
                    </div>
                </CardHeader>

                <CardContent className="space-y-5 pt-6">
                    {pageErrorMessage ? (
                        <div className="rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                <div>
                                    <div className="font-medium">Network data is degraded</div>
                                    <div className="mt-1 text-destructive/85">{pageErrorMessage}</div>
                                </div>
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    onClick={() => void handleReload()}
                                    disabled={isLoading || isRefreshing}
                                >
                                    Retry
                                </Button>
                            </div>
                        </div>
                    ) : null}

                    <Tabs
                        value={activeSection}
                        onValueChange={(nextValue) => navigate(`/network/${nextValue}`)}
                    >
                        <TabsList
                            variant="line"
                            className="h-auto w-full flex-wrap justify-start gap-1 rounded-xl border border-border/70 bg-muted/30 p-1"
                        >
                            {NETWORK_SECTION_ITEMS.map(({ icon: Icon, label, value }) => (
                                <TabsTrigger key={value} value={value} className="px-3 py-2.5">
                                    <Icon className="size-4" />
                                    {label}
                                </TabsTrigger>
                            ))}
                        </TabsList>
                    </Tabs>

                    <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-4">
                        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                            <div>
                                <div className="text-sm font-semibold text-foreground">
                                    {activeSectionItem.label}
                                </div>
                                <div className="mt-1 text-sm leading-6 text-muted-foreground">
                                    {activeSectionItem.description}
                                </div>
                            </div>
                            <Badge variant="outline" className="w-fit border-border/80">
                                Section {NETWORK_SECTION_ITEMS.findIndex((item) => item.value === activeSection) + 1}
                                /{NETWORK_SECTION_ITEMS.length}
                            </Badge>
                        </div>
                    </div>
                </CardContent>
            </Card>

            <Outlet context={contextValue} />
        </div>
    );
};

export default NetworkPage;
