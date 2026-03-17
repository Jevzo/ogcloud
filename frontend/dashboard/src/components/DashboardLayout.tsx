import { startTransition, useEffect, useMemo } from "react";
import { NavLink, Outlet, matchPath, useLocation, useNavigate } from "react-router";
import { BellIcon, LogOutIcon, Settings2Icon } from "lucide-react";

import HeaderSearch from "@/components/HeaderSearch";
import {
    dashboardNavSections,
    dashboardRouteDefinitions,
    type DashboardNavItem,
} from "@/components/dashboard-nav";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
    Sidebar,
    SidebarContent,
    SidebarGroup,
    SidebarGroupContent,
    SidebarGroupLabel,
    SidebarHeader,
    SidebarInset,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    SidebarProvider,
    SidebarTrigger,
} from "@/components/ui/sidebar";
import RequireMinecraftLinkDialog from "@/features/auth/components/require-minecraft-link-dialog";
import { getNetworkSettings } from "@/lib/api";
import { hasAdminAccess } from "@/lib/roles";
import { useAuthStore } from "@/store/auth-store";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import buildVersionRaw from "../../VERSION?raw";

const BUILD_VERSION = buildVersionRaw.trim() || "0.0.0";

const findRouteTitle = (pathname: string) =>
    dashboardRouteDefinitions.find((route) =>
        Boolean(matchPath({ path: route.pattern, end: true }, pathname)),
    )?.title;

const isNavItemActive = (item: DashboardNavItem, pathname: string) =>
    item.matchPatterns.some((pattern) =>
        Boolean(matchPath({ path: pattern, end: pattern === item.href }, pathname)),
    );

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

    const currentPageTitle = useMemo(
        () => findRouteTitle(location.pathname) ?? "Dashboard",
        [location.pathname],
    );
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

    useEffect(() => {
        let cancelled = false;

        const loadShellContext = async () => {
            try {
                const nextSession = await refreshIfNeeded();

                if (!nextSession || cancelled) {
                    return;
                }

                const networkSettings = await getNetworkSettings(nextSession.accessToken);

                if (cancelled) {
                    return;
                }

                setGeneralSettings(networkSettings.general);
            } catch {
                // Keep the last known permission-system value when the shell context cannot refresh.
            }
        };

        void loadShellContext();

        return () => {
            cancelled = true;
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
        <SidebarProvider defaultOpen className="min-h-svh">
            <Sidebar collapsible="icon" className="border-sidebar-border bg-sidebar">
                <SidebarHeader className="h-20 shrink-0 border-b border-sidebar-border/80 px-4 py-0 group-data-[collapsible=icon]:px-3">
                    <NavLink
                        to="/"
                        className="flex h-full w-full min-h-0 items-center gap-3 rounded-none px-0 py-0 outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring/60 group-data-[collapsible=icon]:justify-center"
                    >
                        <img
                            src="/static/logo.webp"
                            alt="OgCloud"
                            className="h-11 w-14 shrink-0 object-contain"
                        />
                        <div className="min-w-0 space-y-1 leading-tight group-data-[collapsible=icon]:hidden">
                            <div className="truncate text-base font-semibold text-sidebar-foreground">
                                OgCloud
                            </div>
                            <div className="text-xs font-medium tracking-[0.08em] text-sidebar-foreground/55">
                                Build {BUILD_VERSION}
                            </div>
                        </div>
                    </NavLink>
                </SidebarHeader>

                <SidebarContent className="pb-4">
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
                                                        <NavLink to={item.href} end={item.href === "/"}>
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
            </Sidebar>

            <SidebarInset className="min-h-svh max-h-svh">
                <header className="sticky top-0 z-20 border-b border-border/70 bg-background/92 px-4 py-3 backdrop-blur-xl sm:px-6 lg:px-8 md:flex md:h-20 md:items-center md:py-0">
                    <div className="flex w-full min-w-0 flex-wrap items-center gap-3 lg:gap-4 md:h-full">
                        <div className="flex min-w-0 items-center gap-3">
                            <SidebarTrigger className="shrink-0" />
                            <h1 className="truncate text-xl font-semibold tracking-tight text-foreground">
                                {currentPageTitle}
                            </h1>
                        </div>

                        <div className="order-3 basis-full md:order-2 md:ml-auto md:max-w-[24rem] md:flex-1 lg:max-w-[28rem]">
                            <HeaderSearch />
                        </div>

                        <div className="order-4 hidden h-8 w-px bg-border/70 md:block" />

                        <div className="order-2 ml-auto shrink-0 md:order-5 md:ml-0">
                            <DropdownMenu>
                                <DropdownMenuTrigger asChild>
                                    <Button
                                        variant="ghost"
                                        className="h-10 rounded-lg px-3 hover:bg-muted/70 sm:px-3.5"
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
                                        <div className="hidden min-w-0 text-left sm:block">
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
                </header>

                <main className="min-h-0 flex-1 overflow-y-auto px-4 py-6 sm:px-6 lg:px-8">
                    <div className="mx-auto flex min-h-full w-full max-w-7xl flex-col">
                        <Outlet />
                    </div>
                </main>
            </SidebarInset>

            <RequireMinecraftLinkDialog />
        </SidebarProvider>
    );
};

export default DashboardLayout;
