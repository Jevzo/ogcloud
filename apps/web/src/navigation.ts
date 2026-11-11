import {
  Activity,
  Bot,
  Globe,
  KeyRound,
  Layers,
  LayoutDashboard,
  Lock,
  type LucideIcon,
  Plug,
  Server,
  Shield,
  UserCog,
  Users,
} from "lucide-react";

export type NavLabel =
  | "overview"
  | "servers"
  | "groups"
  | "players"
  | "permissions"
  | "network"
  | "npcs"
  | "activity"
  | "users"
  | "roles"
  | "clients"
  | "privacy";

export type NavPath =
  | "/"
  | "/servers"
  | "/groups"
  | "/players"
  | "/permissions"
  | "/network"
  | "/npcs"
  | "/activity"
  | "/users"
  | "/roles"
  | "/clients"
  | "/privacy";

export interface NavItem {
  path: NavPath;
  label: NavLabel;
  icon: LucideIcon;
  /** The API permission that opens the page (D-0092); the item is hidden without it. */
  permission: string;
}

export const PRIMARY_NAV: readonly NavItem[] = [
  {
    path: "/",
    label: "overview",
    icon: LayoutDashboard,
    permission: "overview:read",
  },
  {
    path: "/servers",
    label: "servers",
    icon: Server,
    permission: "servers:read",
  },
  { path: "/groups", label: "groups", icon: Layers, permission: "groups:read" },
  {
    path: "/players",
    label: "players",
    icon: Users,
    permission: "players:read",
  },
  {
    path: "/permissions",
    label: "permissions",
    icon: Shield,
    permission: "permissions:read",
  },
  {
    path: "/network",
    label: "network",
    icon: Globe,
    permission: "network:read",
  },
  { path: "/npcs", label: "npcs", icon: Bot, permission: "npcs:read" },
  {
    path: "/activity",
    label: "activity",
    icon: Activity,
    permission: "logs:read",
  },
];

export const ADMIN_NAV: readonly NavItem[] = [
  { path: "/users", label: "users", icon: UserCog, permission: "users:read" },
  { path: "/roles", label: "roles", icon: KeyRound, permission: "roles:read" },
  {
    path: "/clients",
    label: "clients",
    icon: Plug,
    permission: "oauth-clients:read",
  },
  {
    path: "/privacy",
    label: "privacy",
    icon: Lock,
    permission: "privacy:export",
  },
];

export const ALL_NAV: readonly NavItem[] = [...PRIMARY_NAV, ...ADMIN_NAV];

export function allowed(
  items: readonly NavItem[],
  permissions: readonly string[],
): NavItem[] {
  return items.filter((item) => permissions.includes(item.permission));
}
