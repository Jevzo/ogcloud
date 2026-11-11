import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from "@tanstack/react-router";
import { CALLBACK_PATH } from "./auth.ts";
import { Callback } from "./pages/callback.tsx";
import { GroupCreatePage } from "./pages/groups/group-create-page.tsx";
import { GroupPage } from "./pages/groups/group-page.tsx";
import { GroupsPage } from "./pages/groups/groups-page.tsx";
import { NotFound } from "./pages/not-found.tsx";
import { OverviewPage } from "./pages/overview/overview-page.tsx";
import { PermissionGroupPage } from "./pages/permissions/permission-group-page.tsx";
import { PermissionGroupsPage } from "./pages/permissions/permission-groups-page.tsx";
import { Placeholder } from "./pages/placeholder.tsx";
import { PlayerPage } from "./pages/players/player-page.tsx";
import { PlayersPage } from "./pages/players/players-page.tsx";
import { ServerPage } from "./pages/servers/server-page.tsx";
import { ServersPage } from "./pages/servers/servers-page.tsx";
import { AppShell } from "./shell/app-shell.tsx";

const root = createRootRoute({
  component: Outlet,
  notFoundComponent: NotFound,
});

const callback = createRoute({
  getParentRoute: () => root,
  path: CALLBACK_PATH,
  component: Callback,
});

// Every page behind sign-in shares the shell; pages arrive with T-0706 to T-0709.
const app = createRoute({
  getParentRoute: () => root,
  id: "app",
  component: AppShell,
});

const overview = createRoute({
  getParentRoute: () => app,
  path: "/",
  component: OverviewPage,
});
const servers = createRoute({
  getParentRoute: () => app,
  path: "/servers",
  component: ServersPage,
});
const server = createRoute({
  getParentRoute: () => app,
  path: "/servers/$serverId",
  component: ServerPage,
});
const groups = createRoute({
  getParentRoute: () => app,
  path: "/groups",
  component: GroupsPage,
});
const groupCreate = createRoute({
  getParentRoute: () => app,
  path: "/groups/new",
  component: GroupCreatePage,
});
const group = createRoute({
  getParentRoute: () => app,
  path: "/groups/$groupId",
  component: GroupPage,
});
const players = createRoute({
  getParentRoute: () => app,
  path: "/players",
  component: PlayersPage,
});
const player = createRoute({
  getParentRoute: () => app,
  path: "/players/$playerId",
  component: PlayerPage,
});
const permissions = createRoute({
  getParentRoute: () => app,
  path: "/permissions",
  component: PermissionGroupsPage,
});
const permissionGroup = createRoute({
  getParentRoute: () => app,
  path: "/permissions/$permissionGroupId",
  component: PermissionGroupPage,
});
const network = createRoute({
  getParentRoute: () => app,
  path: "/network",
  component: () => <Placeholder label="network" />,
});
const npcs = createRoute({
  getParentRoute: () => app,
  path: "/npcs",
  component: () => <Placeholder label="npcs" />,
});
const activity = createRoute({
  getParentRoute: () => app,
  path: "/activity",
  component: () => <Placeholder label="activity" />,
});
const users = createRoute({
  getParentRoute: () => app,
  path: "/users",
  component: () => <Placeholder label="users" />,
});
const roles = createRoute({
  getParentRoute: () => app,
  path: "/roles",
  component: () => <Placeholder label="roles" />,
});
const clients = createRoute({
  getParentRoute: () => app,
  path: "/clients",
  component: () => <Placeholder label="clients" />,
});
const privacy = createRoute({
  getParentRoute: () => app,
  path: "/privacy",
  component: () => <Placeholder label="privacy" />,
});
const account = createRoute({
  getParentRoute: () => app,
  path: "/account",
  component: () => <Placeholder label="account" />,
});

const routeTree = root.addChildren([
  callback,
  app.addChildren([
    overview,
    servers,
    server,
    groups,
    groupCreate,
    group,
    players,
    player,
    permissions,
    permissionGroup,
    network,
    npcs,
    activity,
    users,
    roles,
    clients,
    privacy,
    account,
  ]),
]);

export const router = createRouter({
  routeTree,
  defaultNotFoundComponent: NotFound,
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
