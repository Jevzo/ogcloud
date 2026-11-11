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
  /** The API permissions that open the page, any one of them (D-0092); the item is hidden without one. */
  permissions: readonly string[];
}

export const PRIMARY_NAV: readonly NavItem[] = [
  {
    path: "/",
    label: "overview",
    icon: LayoutDashboard,
    permissions: ["overview:read"],
  },
  {
    path: "/servers",
    label: "servers",
    icon: Server,
    permissions: ["servers:read"],
  },
  {
    path: "/groups",
    label: "groups",
    icon: Layers,
    permissions: ["groups:read"],
  },
  {
    path: "/players",
    label: "players",
    icon: Users,
    permissions: ["players:read"],
  },
  {
    path: "/permissions",
    label: "permissions",
    icon: Shield,
    permissions: ["permissions:read"],
  },
  {
    path: "/network",
    label: "network",
    icon: Globe,
    permissions: ["network:read"],
  },
  { path: "/npcs", label: "npcs", icon: Bot, permissions: ["npcs:read"] },
  {
    path: "/activity",
    label: "activity",
    icon: Activity,
    permissions: ["logs:read"],
  },
];

export const ADMIN_NAV: readonly NavItem[] = [
  {
    path: "/users",
    label: "users",
    icon: UserCog,
    permissions: ["users:read"],
  },
  {
    path: "/roles",
    label: "roles",
    icon: KeyRound,
    permissions: ["roles:read"],
  },
  {
    path: "/clients",
    label: "clients",
    icon: Plug,
    permissions: ["oauth-clients:read"],
  },
  {
    path: "/privacy",
    label: "privacy",
    icon: Lock,
    permissions: ["privacy:export", "privacy:erase"],
  },
];

export const ALL_NAV: readonly NavItem[] = [...PRIMARY_NAV, ...ADMIN_NAV];

export function allowed(
  items: readonly NavItem[],
  permissions: readonly string[],
): NavItem[] {
  return items.filter((item) =>
    item.permissions.some((permission) => permissions.includes(permission)),
  );
}
