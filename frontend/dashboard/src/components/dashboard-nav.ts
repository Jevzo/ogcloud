import type { LucideIcon } from "lucide-react";
import {
    BellIcon,
    BlocksIcon,
    HouseIcon,
    Layers3Icon,
    NetworkIcon,
    ServerIcon,
    Settings2Icon,
    ShieldCheckIcon,
    UserCogIcon,
    UsersIcon,
} from "lucide-react";

export type DashboardNavRole = "admin";

export interface DashboardNavItem {
    title: string;
    href: string;
    icon: LucideIcon;
    matchPatterns: readonly string[];
    requiredRole?: DashboardNavRole;
    disabledWhenPermissionSystemDisabled?: boolean;
}

export interface DashboardNavSection {
    title: string;
    items: readonly DashboardNavItem[];
}

export interface DashboardRouteDefinition {
    pattern: string;
    title: string;
}

export const dashboardNavSections: readonly DashboardNavSection[] = [
    {
        title: "Operations",
        items: [
            {
                title: "Dashboard",
                href: "/",
                icon: HouseIcon,
                matchPatterns: ["/"],
            },
            {
                title: "Servers",
                href: "/servers",
                icon: ServerIcon,
                matchPatterns: ["/servers", "/servers/:serverId"],
            },
            {
                title: "Groups",
                href: "/groups",
                icon: Layers3Icon,
                matchPatterns: ["/groups", "/groups/:groupName"],
            },
            {
                title: "Players",
                href: "/players",
                icon: UsersIcon,
                matchPatterns: ["/players"],
            },
            {
                title: "Network",
                href: "/network",
                icon: NetworkIcon,
                matchPatterns: [
                    "/network",
                    "/network/overview",
                    "/network/server-settings",
                    "/network/general",
                    "/network/messaging",
                ],
            },
        ],
    },
    {
        title: "Control",
        items: [
            {
                title: "Permissions",
                href: "/permissions",
                icon: ShieldCheckIcon,
                matchPatterns: ["/permissions", "/permissions/:groupName"],
                requiredRole: "admin",
                disabledWhenPermissionSystemDisabled: true,
            },
            {
                title: "Templates",
                href: "/templates",
                icon: BlocksIcon,
                matchPatterns: ["/templates"],
            },
            {
                title: "Inbox",
                href: "/inbox",
                icon: BellIcon,
                matchPatterns: ["/inbox"],
                requiredRole: "admin",
            },
            {
                title: "Web Users",
                href: "/web-users",
                icon: UserCogIcon,
                matchPatterns: ["/web-users"],
                requiredRole: "admin",
            },
            {
                title: "Settings",
                href: "/settings",
                icon: Settings2Icon,
                matchPatterns: ["/settings"],
            },
        ],
    },
] as const;

export const dashboardRouteDefinitions: readonly DashboardRouteDefinition[] = [
    { pattern: "/", title: "Dashboard" },
    { pattern: "/servers", title: "Servers" },
    { pattern: "/servers/:serverId", title: "Server Details" },
    { pattern: "/groups", title: "Groups" },
    { pattern: "/groups/:groupName", title: "Group Details" },
    { pattern: "/players", title: "Players" },
    { pattern: "/inbox", title: "Inbox" },
    { pattern: "/network", title: "Network" },
    { pattern: "/network/overview", title: "Overview" },
    { pattern: "/network/server-settings", title: "Server Settings" },
    { pattern: "/network/general", title: "General" },
    { pattern: "/network/messaging", title: "Messaging" },
    { pattern: "/permissions", title: "Permissions" },
    { pattern: "/permissions/:groupName", title: "Permission Group Details" },
    { pattern: "/templates", title: "Templates" },
    { pattern: "/web-users", title: "Web Users" },
    { pattern: "/settings", title: "Settings" },
] as const;
