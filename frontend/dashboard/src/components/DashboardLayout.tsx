import { startTransition, useEffect, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router";
import { motion } from "motion/react";
import {
  FiActivity,
  FiBell,
  FiFileText,
  FiGrid,
  FiLayers,
  FiLogOut,
  FiSettings,
  FiShield,
  FiUsers,
  FiUserPlus,
} from "react-icons/fi";

import HeaderSearch from "@/components/HeaderSearch";
import RequireMinecraftLinkModal from "@/components/RequireMinecraftLinkModal";
import { getNetworkStatus } from "@/lib/api";
import { hasAdminAccess, normalizeRole } from "@/lib/roles";
import { useAuthStore } from "@/store/auth-store";

interface NavItem {
  label: string;
  to: string;
  icon: typeof FiGrid;
  requiredRole?: string;
}

const NAV_ITEMS = [
  { label: "Dashboard", to: "/", icon: FiGrid },
  { label: "Servers", to: "/servers", icon: FiActivity },
  { label: "Groups", to: "/groups", icon: FiLayers },
  { label: "Players", to: "/players", icon: FiUsers },
  { label: "Network", to: "/network", icon: FiActivity },
  {
    label: "Permissions",
    to: "/permissions",
    icon: FiShield,
    requiredRole: "ADMIN",
  },
  { label: "Templates", to: "/templates", icon: FiFileText },
  {
    label: "Web Users",
    to: "/web-users",
    icon: FiUserPlus,
    requiredRole: "ADMIN",
  },
] satisfies readonly NavItem[];

const DashboardLayout = () => {
  const navigate = useNavigate();
  const logout = useAuthStore((state) => state.logout);
  const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
  const user = useAuthStore((state) => state.session?.user);
  const userInitial = user?.username?.charAt(0).toUpperCase() ?? "U";
  const linkedPlayerHeadUrl = user?.linkedPlayerUuid
    ? `https://mc-heads.net/avatar/${user.linkedPlayerUuid}`
    : null;
  const normalizedUserRole = normalizeRole(user?.role);
  const [clusterHealthState, setClusterHealthState] = useState<
    "loading" | "healthy" | "warning" | "error"
  >("loading");
  const [clusterHealthMessage, setClusterHealthMessage] = useState(
    "Checking cluster status...",
  );
  const visibleNavItems = NAV_ITEMS.filter((item) => {
    if (!item.requiredRole) {
      return true;
    }

    const normalizedRequiredRole = item.requiredRole.trim().toLowerCase();

    if (normalizedRequiredRole === "admin") {
      return hasAdminAccess(user?.role);
    }

    return normalizedUserRole === normalizedRequiredRole;
  });

  useEffect(() => {
    let cancelled = false;

    const loadClusterHealth = async () => {
      try {
        const nextSession = await refreshIfNeeded();

        if (!nextSession || cancelled) {
          return;
        }

        const status = await getNetworkStatus(nextSession.accessToken);

        if (cancelled) {
          return;
        }

        if (status.serverCount === 0 && status.proxyCount === 0) {
          setClusterHealthState("error");
          setClusterHealthMessage(
            "The cloud has not registered any running servers or proxies. Players cannot join the network!",
          );
          return;
        }

        if (status.serverCount === 0) {
          setClusterHealthState("warning");
          setClusterHealthMessage(
            "The cloud has not registered any running servers. Players can join, but no game sessions can be created.",
          );
          return;
        }

        if (status.proxyCount === 0) {
          setClusterHealthState("error");
          setClusterHealthMessage(
            "The cloud has not registered any running proxies. Players cannot join the network!",
          );
          return;
        }

        setClusterHealthState("healthy");
        setClusterHealthMessage(
          "All green. Automated systems found no issues with the cloud. Happy gaming!",
        );
      } catch {
        if (cancelled) {
          return;
        }

        setClusterHealthState("error");
        setClusterHealthMessage(
          "API is not reachable, contact the network administrator. This is a severe issue in a underlying system.",
        );
      }
    };

    void loadClusterHealth();

    const intervalId = window.setInterval(() => {
      void loadClusterHealth();
    }, 10000);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [refreshIfNeeded]);

  const clusterHealthAccentClass =
    clusterHealthState === "healthy"
      ? "border-primary/10 bg-primary/5"
      : clusterHealthState === "warning"
        ? "border-amber-500/20 bg-amber-500/5"
        : clusterHealthState === "error"
          ? "border-red-500/20 bg-red-500/5"
          : "border-slate-700 bg-slate-800/40";
  const clusterHealthTitleClass =
    clusterHealthState === "healthy"
      ? "text-primary"
      : clusterHealthState === "warning"
        ? "text-amber-300"
        : clusterHealthState === "error"
          ? "text-red-300"
          : "text-slate-300";
  const clusterHealthDotClass =
    clusterHealthState === "healthy"
      ? "animate-pulse bg-emerald-500 shadow-[0_0_10px_rgba(16,185,129,0.8)]"
      : clusterHealthState === "warning"
        ? "animate-pulse bg-amber-400 shadow-[0_0_10px_rgba(251,191,36,0.75)]"
        : clusterHealthState === "error"
          ? "animate-pulse bg-red-500 shadow-[0_0_10px_rgba(239,68,68,0.75)]"
          : "bg-slate-500 shadow-[0_0_10px_rgba(100,116,139,0.45)]";

  const handleOpenSettings = () => {
    startTransition(() => {
      navigate("/settings");
    });
  };

  const handleOpenInbox = () => {
    startTransition(() => {
      navigate("/inbox");
    });
  };

  const handleSignOut = () => {
    logout();
    startTransition(() => {
      navigate("/login", { replace: true });
    });
  };

  return (
    <div className="min-h-screen bg-background-dark text-slate-100 lg:h-screen">
      <div className="flex min-h-screen flex-col overflow-hidden lg:h-screen lg:flex-row">
        <motion.aside
          initial={{ x: -24, opacity: 0 }}
          animate={{ x: 0, opacity: 1 }}
          transition={{ duration: 0.45, ease: "easeOut" }}
          className="flex w-full shrink-0 flex-col border-b border-slate-800 bg-slate-900/50 lg:min-h-screen lg:w-64 lg:border-b-0 lg:border-r"
        >
          <div className="flex h-full flex-col">
            <div className="flex items-center gap-3 p-6">
              <img
                src="/static/logo.webp"
                alt="OgCloud"
                className="h-10 w-auto"
              />
              <div>
                <h1 className="text-lg leading-none font-bold text-white">
                  OgCloud
                </h1>
                <p className="mt-1 text-xs text-slate-400">
                  no hustle, no stress
                </p>
              </div>
            </div>

            <nav className="flex-1 space-y-1 overflow-y-auto px-4">
              {visibleNavItems.map(({ label, to, icon: Icon }) => (
                <NavLink
                  key={label}
                  to={to}
                  end={to === "/"}
                  className={({ isActive }) =>
                    `flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium transition-all ${
                      isActive
                        ? "bg-primary/10 text-primary"
                        : "text-slate-400 hover:bg-slate-800 hover:text-primary"
                    }`
                  }
                >
                  <Icon className="h-5 w-5 shrink-0" />
                  <span className="leading-none">{label}</span>
                </NavLink>
              ))}
            </nav>

            <div className="mt-auto p-4">
              <div
                className={`rounded-xl border p-4 ${clusterHealthAccentClass}`}
              >
                <div className="mb-2 flex items-center justify-between">
                  <span
                    className={`text-xs font-semibold uppercase tracking-wider ${clusterHealthTitleClass}`}
                  >
                    Cluster Health
                  </span>
                  <span
                    className={`h-2 w-2 rounded-full ${clusterHealthDotClass}`}
                  />
                </div>
                <p className="text-[10px] leading-relaxed text-slate-400">
                  {clusterHealthMessage}
                </p>
              </div>
            </div>
          </div>
        </motion.aside>

        <div className="flex min-w-0 flex-1 flex-col overflow-y-auto">
          <motion.header
            initial={{ y: -20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.45, ease: "easeOut", delay: 0.08 }}
            className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-800 bg-background-dark/80 px-4 py-4 backdrop-blur-md sm:px-6 lg:px-8"
          >
            <div className="flex flex-1 items-center gap-4">
              <HeaderSearch />
            </div>

            <div className="flex items-center gap-4 sm:gap-6">
              {hasAdminAccess(user?.role) ? (
                <button
                  type="button"
                  onClick={handleOpenInbox}
                  className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                  aria-label="Inbox"
                >
                  <FiBell className="h-5 w-5" />
                </button>
              ) : null}
              <button
                type="button"
                onClick={handleOpenSettings}
                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                aria-label="Settings"
              >
                <FiSettings className="h-5 w-5" />
              </button>
              <div className="flex items-center gap-3 border-l border-slate-800 pl-4 sm:pl-6">
                <div className="hidden text-right sm:block">
                  <p className="text-sm font-semibold text-white">
                    {user?.username ?? "none"}
                  </p>
                  <p className="flex items-center justify-end gap-1 text-xs text-emerald-500">
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                    {user?.role ?? "none"}
                  </p>
                </div>
                {linkedPlayerHeadUrl ? (
                  <img
                    src={linkedPlayerHeadUrl}
                    alt={`${user?.username ?? "User"} Minecraft avatar`}
                    className="h-10 w-10 rounded-full bg-slate-700 object-cover"
                    referrerPolicy="no-referrer"
                  />
                ) : (
                  <div
                    className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-700 text-sm font-semibold text-white"
                    aria-label="User avatar"
                  >
                    {userInitial}
                  </div>
                )}
                <button
                  type="button"
                  onClick={handleSignOut}
                  className="button-hover-lift rounded-lg bg-primary/10 p-2 text-primary transition-colors hover:bg-primary hover:text-slate-950"
                  aria-label="Sign out"
                >
                  <FiLogOut className="h-4 w-4" />
                </button>
              </div>
            </div>
          </motion.header>

          <main className="flex-1 bg-background-dark px-4 py-6 sm:px-6 lg:px-8">
            <div className="mx-auto flex h-full w-full max-w-7xl flex-col">
              <Outlet />
            </div>
          </main>

          <footer className="mt-auto flex flex-col gap-2 border-t border-slate-800 px-4 py-5 text-xs text-slate-500 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
            <div className="flex flex-col gap-1 sm:flex-row sm:gap-4">
              <span>Authenticated as {user?.email ?? "unknown user"}</span>
            </div>
            <div>OgCloud Build v0.0.1</div>
          </footer>
        </div>
      </div>
      <RequireMinecraftLinkModal />
    </div>
  );
};

export default DashboardLayout;
