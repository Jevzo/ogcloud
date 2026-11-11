import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from "@tanstack/react-router";
import { CALLBACK_PATH, SETUP_PATH } from "./auth.ts";
import { AccountPage } from "./pages/account/account-page.tsx";
import { ActivityPage } from "./pages/activity/activity-page.tsx";
import { Callback } from "./pages/callback.tsx";
import { ClientPage } from "./pages/clients/client-page.tsx";
import { ClientsPage } from "./pages/clients/clients-page.tsx";
import { GroupCreatePage } from "./pages/groups/group-create-page.tsx";
import { GroupPage } from "./pages/groups/group-page.tsx";
import { GroupsPage } from "./pages/groups/groups-page.tsx";
import { NetworkPage } from "./pages/network/network-page.tsx";
import { NotFound } from "./pages/not-found.tsx";
import { NpcCreatePage } from "./pages/npcs/npc-create-page.tsx";
import { NpcPage } from "./pages/npcs/npc-page.tsx";
import { NpcsPage } from "./pages/npcs/npcs-page.tsx";
import { OverviewPage } from "./pages/overview/overview-page.tsx";
import { PermissionGroupPage } from "./pages/permissions/permission-group-page.tsx";
import { PermissionGroupsPage } from "./pages/permissions/permission-groups-page.tsx";
import { PlayerPage } from "./pages/players/player-page.tsx";
import { PlayersPage } from "./pages/players/players-page.tsx";
import { PrivacyPage } from "./pages/privacy/privacy-page.tsx";
import { RolePage } from "./pages/roles/role-page.tsx";
import { RolesPage } from "./pages/roles/roles-page.tsx";
import { ServerPage } from "./pages/servers/server-page.tsx";
import { ServersPage } from "./pages/servers/servers-page.tsx";
import { SetupPage, WELCOME_PATH } from "./pages/setup/setup-page.tsx";
import { WelcomePage } from "./pages/setup/welcome-page.tsx";
import { UserPage } from "./pages/users/user-page.tsx";
import { UsersPage } from "./pages/users/users-page.tsx";
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
  component: NetworkPage,
});
const npcs = createRoute({
  getParentRoute: () => app,
  path: "/npcs",
  component: NpcsPage,
});
const npcCreate = createRoute({
  getParentRoute: () => app,
  path: "/npcs/new",
  component: NpcCreatePage,
});
const npc = createRoute({
  getParentRoute: () => app,
  path: "/npcs/$npcId",
  component: NpcPage,
});
const activity = createRoute({
  getParentRoute: () => app,
  path: "/activity",
  component: ActivityPage,
});
const users = createRoute({
  getParentRoute: () => app,
  path: "/users",
  component: UsersPage,
});
const user = createRoute({
  getParentRoute: () => app,
  path: "/users/$userId",
  component: UserPage,
});
const roles = createRoute({
  getParentRoute: () => app,
  path: "/roles",
  component: RolesPage,
});
const role = createRoute({
  getParentRoute: () => app,
  path: "/roles/$roleId",
  component: RolePage,
});
const clients = createRoute({
  getParentRoute: () => app,
  path: "/clients",
  component: ClientsPage,
});
const client = createRoute({
  getParentRoute: () => app,
  path: "/clients/$clientId",
  component: ClientPage,
});
const privacy = createRoute({
  getParentRoute: () => app,
  path: "/privacy",
  component: PrivacyPage,
});
const account = createRoute({
  getParentRoute: () => app,
  path: "/account",
  component: AccountPage,
});

const setup = createRoute({
  getParentRoute: () => root,
  path: SETUP_PATH,
  component: SetupPage,
});
const welcome = createRoute({
  getParentRoute: () => root,
  path: WELCOME_PATH,
  component: WelcomePage,
});

const routeTree = root.addChildren([
  callback,
  setup,
  welcome,
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
    npcCreate,
    npc,
    activity,
    users,
    user,
    roles,
    role,
    clients,
    client,
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
