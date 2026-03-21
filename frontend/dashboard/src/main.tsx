import { type ReactElement, StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, Navigate, type RouteObject, RouterProvider } from "react-router";

import "@/index.css";

import AppShell from "@/components/AppShell";
import DashboardLayout from "@/components/DashboardLayout";
import GuestOnlyRoute from "@/components/GuestOnlyRoute";
import RequireAdminAccess from "@/components/RequireAdminAccess";
import RequireAuth from "@/components/RequireAuth";
import LoginPage from "@/features/auth/components/login-page";
import DashboardHome from "@/features/dashboard/components/dashboard-home-page";
import GroupDetailsPage from "@/features/groups/components/group-details-page";
import GroupsPage from "@/features/groups/components/groups-page";
import InboxPage from "@/features/inbox/components/inbox-page";
import NetworkPage from "@/features/network/components/network-page";
import PermissionGroupDetailsPage from "@/features/permissions/components/permission-group-details-page";
import PermissionsPage from "@/features/permissions/components/permissions-page";
import PlayerDetailsPage from "@/features/players/components/player-details-page";
import PlayersPage from "@/features/players/components/players-page";
import ServerDetailsPage from "@/features/servers/components/server-details-page";
import ServersPage from "@/features/servers/components/servers-page";
import SettingsPage from "@/features/settings/components/settings-page";
import TemplatesPage from "@/features/templates/components/templates-page";
import WebUsersPage from "@/features/web-users/components/web-users-page";
import NotFound from "@/features/error/components/not-found-page";

const withAdminAccess = (element: ReactElement) => (
    <RequireAdminAccess>{element}</RequireAdminAccess>
);

const dashboardRoutes: RouteObject[] = [
    { index: true, element: <DashboardHome /> },
    { path: "servers", element: <ServersPage /> },
    { path: "servers/:serverId", element: <ServerDetailsPage /> },
    { path: "groups", element: <GroupsPage /> },
    { path: "groups/:groupName", element: <GroupDetailsPage /> },
    { path: "players", element: <PlayersPage /> },
    { path: "players/:playerUuid", element: <PlayerDetailsPage /> },
    { path: "inbox", element: withAdminAccess(<InboxPage />) },
    { path: "network", element: <NetworkPage /> },
    { path: "network/:section", element: <Navigate to="/network" replace /> },
    { path: "permissions", element: withAdminAccess(<PermissionsPage />) },
    {
        path: "permissions/:groupName",
        element: withAdminAccess(<PermissionGroupDetailsPage />),
    },
    { path: "templates", element: <TemplatesPage /> },
    { path: "web-users", element: withAdminAccess(<WebUsersPage />) },
    { path: "settings", element: <SettingsPage /> },
];

const appRoutes: RouteObject[] = [
    {
        element: <AppShell />,
        errorElement: <NotFound />,
        children: [
            {
                path: "/login",
                element: (
                    <GuestOnlyRoute>
                        <LoginPage />
                    </GuestOnlyRoute>
                ),
            },
            {
                path: "/",
                element: (
                    <RequireAuth>
                        <DashboardLayout />
                    </RequireAuth>
                ),
                children: dashboardRoutes,
            },
        ],
    },
];

const rootElement = document.getElementById("root");

if (!rootElement) {
    throw new Error("Root element '#root' was not found.");
}

const router = createBrowserRouter(appRoutes);

createRoot(rootElement).render(
    <StrictMode>
        <RouterProvider router={router} />
    </StrictMode>,
);
