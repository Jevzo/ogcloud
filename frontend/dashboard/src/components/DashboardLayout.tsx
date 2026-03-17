import { Fragment, startTransition, useEffect, useMemo, useState } from "react";
import {
    Link,
    NavLink,
    Outlet,
    matchPath,
    useLocation,
    useNavigate,
} from "react-router";
import {
    BellIcon,
    ChevronRightIcon,
    LogOutIcon,
    Settings2Icon,
    ShieldAlertIcon,
} from "lucide-react";

import HeaderSearch from "@/components/HeaderSearch";
import RequireMinecraftLinkModal from "@/components/RequireMinecraftLinkModal";
import {
    dashboardNavSections,
    dashboardRouteDefinitions,
    type DashboardNavItem,
} from "@/components/dashboard-nav";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import {
    Breadcrumb,
    BreadcrumbItem,
    BreadcrumbLink,
    BreadcrumbList,
    BreadcrumbPage,
    BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Separator } from "@/components/ui/separator";
import {
    Sidebar,
    SidebarContent,
    SidebarFooter,
    SidebarGroup,
    SidebarGroupContent,
    SidebarGroupLabel,
    SidebarHeader,
    SidebarInset,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    SidebarProvider,
    SidebarRail,
    SidebarSeparator,
    SidebarTrigger,
} from "@/components/ui/sidebar";
import { getNetworkSettings, getNetworkStatus } from "@/lib/api";
import { hasAdminAccess } from "@/lib/roles";
import { useAuthStore } from "@/store/auth-store";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import type { NetworkStatusRecord } from "@/types/network";
import buildVersionRaw from "../../VERSION?raw";

interface BreadcrumbEntry {
    href: string;
    title: string;
}

type ClusterHealthState = "checking" | "healthy" | "warning" | "critical";

const BUILD_VERSION = buildVersionRaw.trim() || "0.0.0";

const findRouteTitle = (pathname: string) =>
    dashboardRouteDefinitions.find((route) =>
        Boolean(matchPath({ path: route.pattern, end: true }, pathname)),
    )?.title;

const resolveBreadcrumbs = (pathname: string): BreadcrumbEntry[] => {
    if (pathname === "/") {
        return [{ href: "/", title: "Dashboard" }];
    }

    const segments = pathname.split("/").filter(Boolean);
    const breadcrumbs: BreadcrumbEntry[] = [];

    for (let index = 0; index < segments.length; index += 1) {
        const href = `/${segments.slice(0, index + 1).join("/")}`;
        const title = findRouteTitle(href);

        if (title) {
            breadcrumbs.push({ href, title });
        }
    }

    return breadcrumbs.length > 0 ? breadcrumbs : [{ href: pathname, title: "Dashboard" }];
};

const isNavItemActive = (item: DashboardNavItem, pathname: string) =>
    item.matchPatterns.some((pattern) =>
        Boolean(matchPath({ path: pattern, end: pattern === item.href }, pathname)),
    );

const getClusterHealthPresentation = (
    healthState: ClusterHealthState,
    status: NetworkStatusRecord | null,
    message: string,
) => {
    if (healthState === "healthy") {
        return {
            badgeClassName:
                "border-emerald-500/30 bg-emerald-500/12 text-emerald-300 hover:bg-emerald-500/12",
            dotClassName: "bg-emerald-400 shadow-[0_0_0_4px_rgba(16,185,129,0.14)]",
            label: "Healthy",
            message,
            metrics: status,
        };
    }

    if (healthState === "warning") {
        return {
            badgeClassName:
                "border-amber-500/30 bg-amber-500/12 text-amber-300 hover:bg-amber-500/12",
            dotClassName: "bg-amber-400 shadow-[0_0_0_4px_rgba(245,158,11,0.14)]",
            label: "Warning",
            message,
            metrics: status,
        };
    }

    if (healthState === "critical") {
        return {
            badgeClassName:
                "border-red-500/30 bg-red-500/12 text-red-300 hover:bg-red-500/12",
            dotClassName: "bg-red-400 shadow-[0_0_0_4px_rgba(239,68,68,0.14)]",
            label: "Critical",
            message,
            metrics: status,
        };
    }

    return {
        badgeClassName:
            "border-border bg-muted/60 text-muted-foreground hover:bg-muted/60",
        dotClassName: "bg-slate-400 shadow-[0_0_0_4px_rgba(148,163,184,0.12)]",
        label: "Checking",
        message,
        metrics: status,
    };
};

const DashboardLayout = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const logout = useAuthStore((state) => state.logout);
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const user = useAuthStore((state) => state.session?.user);
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled,
    );
    const setGeneralSettings = useNetworkSettingsStore((state) => state.setGeneral);

    const [clusterHealthState, setClusterHealthState] = useState<ClusterHealthState>("checking");
    const [clusterHealthMessage, setClusterHealthMessage] = useState(
        "Polling network health and shell context.",
    );
    const [clusterStatus, setClusterStatus] = useState<NetworkStatusRecord | null>(null);

    const breadcrumbs = useMemo(
        () => resolveBreadcrumbs(location.pathname),
        [location.pathname],
    );
    const currentPageTitle = breadcrumbs[breadcrumbs.length - 1]?.title ?? "Dashboard";
    const userInitial = user?.username?.charAt(0).toUpperCase() ?? "U";
    const linkedPlayerHeadUrl = user?.linkedPlayerUuid
        ? `https://mc-heads.net/avatar/${user.linkedPlayerUuid}`
        : null;

    const visibleNavSections = useMemo(
        () =>
            dashboardNavSections
                .map((section) => ({
                    ...section,
                    items: section.items.filter((item) => {
                        if (item.requiredRole === "admin") {
                            return hasAdminAccess(user?.role);
                        }

                        return true;
                    }),
                }))
                .filter((section) => section.items.length > 0),
        [user?.role],
    );

    const clusterPresentation = useMemo(
        () =>
            getClusterHealthPresentation(
                clusterHealthState,
                clusterStatus,
                clusterHealthMessage,
            ),
        [clusterHealthMessage, clusterHealthState, clusterStatus],
    );

    useEffect(() => {
        let cancelled = false;

        const loadShellContext = async () => {
            try {
                const nextSession = await refreshIfNeeded();

                if (!nextSession || cancelled) {
                    return;
                }

                const [status, networkSettings] = await Promise.all([
                    getNetworkStatus(nextSession.accessToken),
                    getNetworkSettings(nextSession.accessToken),
                ]);

                if (cancelled) {
                    return;
                }

                setClusterStatus(status);
                setGeneralSettings(networkSettings.general);

                if (status.proxyCount === 0) {
                    setClusterHealthState("critical");
                    setClusterHealthMessage(
                        "No running proxies are registered. Players cannot reach the network.",
                    );
                    return;
                }

                if (status.serverCount === 0) {
                    setClusterHealthState("warning");
                    setClusterHealthMessage(
                        "No game servers are currently registered. Players can connect but no sessions can start.",
                    );
                    return;
                }

                setClusterHealthState("healthy");
                setClusterHealthMessage(
                    "Proxy, server, and player telemetry look healthy from the dashboard edge.",
                );
            } catch {
                if (cancelled) {
                    return;
                }

                setClusterHealthState("critical");
                setClusterHealthMessage(
                    "The dashboard could not reach the API for shell health checks.",
                );
            }
        };

        void loadShellContext();

        const intervalId = window.setInterval(() => {
            void loadShellContext();
        }, 10_000);

        return () => {
            cancelled = true;
            window.clearInterval(intervalId);
        };
    }, [refreshIfNeeded, setGeneralSettings]);

    const navigateTo = (href: string) => {
        startTransition(() => {
            navigate(href);
        });
    };

    const handleSignOut = () => {
        logout();
        startTransition(() => {
            navigate("/login", { replace: true });
        });
    };

    return (
        <SidebarProvider
            defaultOpen
            className="min-h-screen bg-[radial-gradient(circle_at_top,rgba(37,99,235,0.12),transparent_24%),linear-gradient(180deg,rgba(4,9,19,0.96),rgba(7,13,24,1))]"
        >
            <Sidebar
                collapsible="icon"
                variant="inset"
                className="border-sidebar-border/80 bg-transparent"
            >
                <SidebarHeader className="gap-3 p-3">
                    <div className="flex items-center gap-3 rounded-2xl border border-sidebar-border/80 bg-sidebar-accent/50 px-3 py-3">
                        <img
                            src="/static/logo.webp"
                            alt="OgCloud"
                            className="h-10 w-10 rounded-xl object-cover shadow-sm"
                        />
                        <div className="min-w-0 flex-1 group-data-[collapsible=icon]:hidden">
                            <div className="flex items-center gap-2">
                                <h1 className="truncate text-sm font-semibold text-sidebar-foreground">
                                    OgCloud
                                </h1>
                                <Badge variant="outline" className="border-primary/30 text-primary">
                                    Build {BUILD_VERSION}
                                </Badge>
                            </div>
                            <p className="mt-1 text-xs text-sidebar-foreground/70">
                                Kubernetes-native network operations for Minecraft infrastructure.
                            </p>
                        </div>
                    </div>
                </SidebarHeader>

                <SidebarSeparator />

                <SidebarContent className="gap-2 px-2 pb-2">
                    {visibleNavSections.map((section) => (
                        <SidebarGroup key={section.title}>
                            <SidebarGroupLabel>{section.title}</SidebarGroupLabel>
                            <SidebarGroupContent>
                                <SidebarMenu>
                                    {section.items.map((item) => {
                                        const Icon = item.icon;
                                        const isActive = isNavItemActive(item, location.pathname);
                                        const isDisabled =
                                            item.disabledWhenPermissionSystemDisabled &&
                                            !permissionSystemEnabled;

                                        return (
                                            <SidebarMenuItem key={item.href}>
                                                {isDisabled ? (
                                                    <SidebarMenuButton
                                                        disabled
                                                        tooltip={`${item.title} is unavailable while the permission system is disabled.`}
                                                        className="cursor-not-allowed opacity-45"
                                                    >
                                                        <Icon />
                                                        <span>{item.title}</span>
                                                    </SidebarMenuButton>
                                                ) : (
                                                    <SidebarMenuButton
                                                        asChild
                                                        isActive={isActive}
                                                        tooltip={item.title}
                                                    >
                                                        <NavLink
                                                            to={item.href}
                                                            end={item.href === "/"}
                                                        >
                                                            <Icon />
                                                            <span>{item.title}</span>
                                                        </NavLink>
                                                    </SidebarMenuButton>
                                                )}
                                            </SidebarMenuItem>
                                        );
                                    })}
                                </SidebarMenu>
                            </SidebarGroupContent>
                        </SidebarGroup>
                    ))}
                </SidebarContent>

                <SidebarFooter className="gap-3 p-3">
                    <Card className="border-sidebar-border/80 bg-sidebar-accent/40 shadow-none">
                        <CardHeader className="pb-3">
                            <div className="flex items-center justify-between gap-3">
                                <div className="flex items-center gap-2">
                                    <span
                                        className={`size-2.5 rounded-full ${clusterPresentation.dotClassName}`}
                                        aria-hidden="true"
                                    />
                                    <CardTitle className="text-sm text-sidebar-foreground">
                                        Cluster Health
                                    </CardTitle>
                                </div>
                                <Badge
                                    variant="outline"
                                    className={clusterPresentation.badgeClassName}
                                >
                                    {clusterPresentation.label}
                                </Badge>
                            </div>
                            <CardDescription className="text-xs leading-relaxed text-sidebar-foreground/70 group-data-[collapsible=icon]:hidden">
                                {clusterPresentation.message}
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="grid grid-cols-3 gap-2 pt-0 group-data-[collapsible=icon]:hidden">
                            <div className="rounded-xl border border-sidebar-border/70 bg-background/50 px-3 py-2">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-sidebar-foreground/55">
                                    Servers
                                </div>
                                <div className="mt-1 text-sm font-semibold text-sidebar-foreground">
                                    {clusterPresentation.metrics?.serverCount ?? "--"}
                                </div>
                            </div>
                            <div className="rounded-xl border border-sidebar-border/70 bg-background/50 px-3 py-2">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-sidebar-foreground/55">
                                    Proxies
                                </div>
                                <div className="mt-1 text-sm font-semibold text-sidebar-foreground">
                                    {clusterPresentation.metrics?.proxyCount ?? "--"}
                                </div>
                            </div>
                            <div className="rounded-xl border border-sidebar-border/70 bg-background/50 px-3 py-2">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-sidebar-foreground/55">
                                    Players
                                </div>
                                <div className="mt-1 text-sm font-semibold text-sidebar-foreground">
                                    {clusterPresentation.metrics?.onlinePlayers ?? "--"}
                                </div>
                            </div>
                        </CardContent>
                    </Card>
                </SidebarFooter>

                <SidebarRail />
            </Sidebar>

            <SidebarInset className="min-h-screen border border-border/70 bg-background/92 shadow-2xl shadow-black/20 backdrop-blur">
                <header className="sticky top-0 z-20 border-b border-border/70 bg-background/88 px-4 py-3 backdrop-blur-xl sm:px-6 lg:px-8">
                    <div className="flex flex-col gap-4">
                        <div className="flex items-start justify-between gap-4">
                            <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-3">
                                    <SidebarTrigger className="-ml-1" />
                                    <Separator
                                        orientation="vertical"
                                        className="hidden h-5 sm:block"
                                    />
                                    <div className="min-w-0">
                                        <Breadcrumb>
                                            <BreadcrumbList>
                                                {breadcrumbs.map((breadcrumb, index) => (
                                                    <Fragment key={breadcrumb.href}>
                                                        <BreadcrumbItem>
                                                            {index === breadcrumbs.length - 1 ? (
                                                                <BreadcrumbPage>
                                                                    {breadcrumb.title}
                                                                </BreadcrumbPage>
                                                            ) : (
                                                                <BreadcrumbLink asChild>
                                                                    <Link to={breadcrumb.href}>
                                                                        {breadcrumb.title}
                                                                    </Link>
                                                                </BreadcrumbLink>
                                                            )}
                                                        </BreadcrumbItem>
                                                        {index < breadcrumbs.length - 1 ? (
                                                            <BreadcrumbSeparator>
                                                                <ChevronRightIcon />
                                                            </BreadcrumbSeparator>
                                                        ) : null}
                                                    </Fragment>
                                                ))}
                                            </BreadcrumbList>
                                        </Breadcrumb>
                                        <div className="mt-2 flex flex-wrap items-center gap-2">
                                            <h2 className="text-lg font-semibold tracking-tight text-foreground">
                                                {currentPageTitle}
                                            </h2>
                                            <Badge variant="outline" className="border-border/80">
                                                v{BUILD_VERSION}
                                            </Badge>
                                            {!permissionSystemEnabled ? (
                                                <Badge
                                                    variant="outline"
                                                    className="border-amber-500/30 bg-amber-500/10 text-amber-300"
                                                >
                                                    Permission system disabled
                                                </Badge>
                                            ) : null}
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div className="hidden items-center gap-2 lg:flex">
                                {hasAdminAccess(user?.role) ? (
                                    <Button
                                        variant="ghost"
                                        size="icon-sm"
                                        onClick={() => navigateTo("/inbox")}
                                        aria-label="Open inbox"
                                    >
                                        <BellIcon />
                                    </Button>
                                ) : null}

                                <Button
                                    variant="ghost"
                                    size="icon-sm"
                                    onClick={() => navigateTo("/settings")}
                                    aria-label="Open settings"
                                >
                                    <Settings2Icon />
                                </Button>

                                <DropdownMenu>
                                    <DropdownMenuTrigger asChild>
                                        <Button
                                            variant="ghost"
                                            className="h-auto rounded-2xl px-2 py-1.5"
                                        >
                                            <Avatar size="lg">
                                                {linkedPlayerHeadUrl ? (
                                                    <AvatarImage
                                                        src={linkedPlayerHeadUrl}
                                                        alt={`${user?.username ?? "User"} Minecraft avatar`}
                                                        referrerPolicy="no-referrer"
                                                    />
                                                ) : null}
                                                <AvatarFallback>{userInitial}</AvatarFallback>
                                            </Avatar>
                                            <div className="min-w-0 text-left">
                                                <div className="truncate text-sm font-medium text-foreground">
                                                    {user?.username ?? "Unknown user"}
                                                </div>
                                                <div className="truncate text-xs text-muted-foreground">
                                                    {user?.email ?? "No email"}
                                                </div>
                                            </div>
                                        </Button>
                                    </DropdownMenuTrigger>
                                    <DropdownMenuContent
                                        align="end"
                                        className="w-64 min-w-64"
                                        sideOffset={10}
                                    >
                                        <DropdownMenuLabel className="space-y-1">
                                            <div className="font-medium text-foreground">
                                                {user?.username ?? "Unknown user"}
                                            </div>
                                            <div className="truncate text-xs text-muted-foreground">
                                                {user?.email ?? "No email"}
                                            </div>
                                        </DropdownMenuLabel>
                                        <DropdownMenuSeparator />
                                        <DropdownMenuItem onSelect={() => navigateTo("/settings")}>
                                            <Settings2Icon />
                                            Settings
                                        </DropdownMenuItem>
                                        {hasAdminAccess(user?.role) ? (
                                            <DropdownMenuItem onSelect={() => navigateTo("/inbox")}>
                                                <BellIcon />
                                                Inbox
                                            </DropdownMenuItem>
                                        ) : null}
                                        <DropdownMenuSeparator />
                                        <DropdownMenuItem onSelect={handleSignOut}>
                                            <LogOutIcon />
                                            Sign out
                                        </DropdownMenuItem>
                                    </DropdownMenuContent>
                                </DropdownMenu>
                            </div>
                        </div>

                        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                            <div className="w-full lg:max-w-xl">
                                <HeaderSearch />
                            </div>
                            <div className="flex items-center justify-between gap-3 lg:hidden">
                                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                                    <ShieldAlertIcon className="size-4 text-primary" />
                                    <span>{clusterPresentation.label}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    {hasAdminAccess(user?.role) ? (
                                        <Button
                                            variant="ghost"
                                            size="icon-sm"
                                            onClick={() => navigateTo("/inbox")}
                                            aria-label="Open inbox"
                                        >
                                            <BellIcon />
                                        </Button>
                                    ) : null}
                                    <Button
                                        variant="ghost"
                                        size="icon-sm"
                                        onClick={() => navigateTo("/settings")}
                                        aria-label="Open settings"
                                    >
                                        <Settings2Icon />
                                    </Button>
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        onClick={handleSignOut}
                                        className="rounded-xl"
                                    >
                                        <LogOutIcon />
                                        Sign out
                                    </Button>
                                </div>
                            </div>
                        </div>
                    </div>
                </header>

                <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
                    <div className="mx-auto flex h-full w-full max-w-7xl flex-col">
                        <Outlet />
                    </div>
                </main>

                <footer className="border-t border-border/70 px-4 py-4 text-xs text-muted-foreground sm:px-6 lg:px-8">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                        <span>Authenticated as {user?.email ?? "unknown user"}</span>
                        <span>OgCloud dashboard build v{BUILD_VERSION}</span>
                    </div>
                </footer>
            </SidebarInset>

            <RequireMinecraftLinkModal />
        </SidebarProvider>
    );
};

export default DashboardLayout;
