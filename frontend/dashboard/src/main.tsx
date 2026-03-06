import { StrictMode, type ReactElement } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider, type RouteObject } from "react-router";

import "@/index.css";

import AppShell from "@/components/AppShell";
import DashboardLayout from "@/components/DashboardLayout";
import GuestOnlyRoute from "@/components/GuestOnlyRoute";
import RequireAdminAccess from "@/components/RequireAdminAccess";
import RequireAuth from "@/components/RequireAuth";
import DashboardHome from "@/pages/DashboardHome";
import GroupDetailsPage from "@/pages/GroupDetailsPage";
import GroupsPage from "@/pages/GroupsPage";
import InboxPage from "@/pages/InboxPage";
import LoginPage from "@/pages/LoginPage";
import NetworkPage from "@/pages/NetworkPage";
import NotFound from "@/pages/NotFound";
import PermissionGroupDetailsPage from "@/pages/PermissionGroupDetailsPage";
import PermissionsPage from "@/pages/PermissionsPage";
import PlayersPage from "@/pages/PlayersPage";
import ServerDetailsPage from "@/pages/ServerDetailsPage";
import ServersPage from "@/pages/ServersPage";
import SettingsPage from "@/pages/SettingsPage";
import TemplatesPage from "@/pages/TemplatesPage";
import WebUsersPage from "@/pages/WebUsersPage";

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
  { path: "inbox", element: withAdminAccess(<InboxPage />) },
  { path: "network", element: <NetworkPage /> },
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
  </StrictMode>
);
