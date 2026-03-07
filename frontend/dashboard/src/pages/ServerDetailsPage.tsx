import { useCallback, useEffect, useState } from "react";
import { motion } from "motion/react";
import {
  FiArrowLeft,
  FiChevronLeft,
  FiChevronRight,
  FiExternalLink,
  FiServer,
  FiTerminal,
  FiUsers,
} from "react-icons/fi";
import { Link, useParams } from "react-router";

import AppToasts from "@/components/AppToasts";
import ExecuteCommandModal from "@/components/ExecuteCommandModal";
import ServerActionButtons from "@/components/ServerActionButtons";
import TableRefreshButton from "@/components/TableRefreshButton";
import { getServerById, listOnlinePlayers } from "@/lib/api";
import { hasAdminAccess } from "@/lib/roles";
import {
  getServerActionSuccessMessage,
  runServerAction,
} from "@/lib/server-actions";
import {
  formatDateTime,
  formatMemoryMb,
  formatTps,
  getServerStateTone,
} from "@/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { PaginatedResponse } from "@/types/dashboard";
import type { OnlinePlayerRecord, ServerActionKind, ServerRecord } from "@/types/server";

const PLAYER_PAGE_SIZE = 5;

const EMPTY_PLAYER_PAGE: PaginatedResponse<OnlinePlayerRecord> = {
  items: [],
  page: 0,
  size: PLAYER_PAGE_SIZE,
  totalItems: 0,
};

const ServerDetailsPage = () => {
  const params = useParams();
  const serverId = decodeURIComponent(params.serverId ?? "");
  const session = useAuthStore((state) => state.session);
  const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
  const canExecuteCommands = hasAdminAccess(session?.user.role);

  const [server, setServer] = useState<ServerRecord | null>(null);
  const [runtimeSnapshot, setRuntimeSnapshot] = useState<ServerRecord | null>(null);
  const [playerPage, setPlayerPage] =
    useState<PaginatedResponse<OnlinePlayerRecord>>(EMPTY_PLAYER_PAGE);
  const [isLoading, setIsLoading] = useState(true);
  const [isPlayerPageLoading, setIsPlayerPageLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [activeActionKey, setActiveActionKey] = useState<string | null>(null);
  const [playerPageIndex, setPlayerPageIndex] = useState(0);
  const [isCommandModalOpen, setIsCommandModalOpen] = useState(false);

  const getValidAccessToken = useCallback(async () => {
    const nextSession = await refreshIfNeeded();

    if (!nextSession) {
      throw new Error("Your session expired. Please sign in again.");
    }

    return nextSession.accessToken;
  }, [refreshIfNeeded]);

  const loadServerDetails = useCallback(async (showLoading = true) => {
    if (!serverId) {
      setErrorMessage("Missing server ID.");
      setIsLoading(false);
      setIsPlayerPageLoading(false);
      return;
    }

    if (showLoading) {
      setIsLoading(true);
    }

    setIsPlayerPageLoading(true);

    try {
      const accessToken = await getValidAccessToken();
      const nextServer = await getServerById(accessToken, serverId);
      const nextPlayerPage = await listOnlinePlayers(accessToken, {
        page: playerPageIndex,
        proxyId: nextServer.type.toUpperCase() === "PROXY" ? serverId : undefined,
        serverId: nextServer.type.toUpperCase() === "PROXY" ? undefined : serverId,
        size: PLAYER_PAGE_SIZE,
      });

      setServer(nextServer);
      setRuntimeSnapshot(nextServer);
      setPlayerPage(nextPlayerPage);
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to load this server."
      );
    } finally {
      setIsLoading(false);
      setIsPlayerPageLoading(false);
    }
  }, [getValidAccessToken, playerPageIndex, serverId]);

  useEffect(() => {
    void loadServerDetails(true);
  }, [loadServerDetails]);

  useEffect(() => {
    setPlayerPageIndex(0);
    setRuntimeSnapshot(null);
  }, [serverId]);

  useEffect(() => {
    if (!serverId) {
      return;
    }

    let intervalId = 0;
    let cancelled = false;

    const refreshRuntimeStats = async () => {
      try {
        const accessToken = await getValidAccessToken();
        const nextServer = await getServerById(accessToken, serverId);

        if (cancelled) {
          return;
        }

        setRuntimeSnapshot(nextServer);
      } catch {
        // Keep the last successful runtime snapshot visible when background refresh fails.
      }
    };

    intervalId = window.setInterval(() => {
      void refreshRuntimeStats();
    }, 10000);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [getValidAccessToken, serverId]);

  useEffect(() => {
    if (!successMessage) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      setSuccessMessage(null);
    }, 3000);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [successMessage]);

  const handleServerAction = async (
    nextServerId: string,
    action: ServerActionKind
  ) => {
    const actionKey = `${nextServerId}:${action}`;
    setActiveActionKey(actionKey);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      await runServerAction(accessToken, nextServerId, action);
      setSuccessMessage(getServerActionSuccessMessage(nextServerId, action));
      await loadServerDetails(false);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to execute the selected server action."
      );
    } finally {
      setActiveActionKey(null);
    }
  };

  const infoRows = server
    ? [
        ["Server ID", server.id],
        ["Display Name", server.displayName],
        ["Group", server.group],
        ["Type", server.type],
        ["State", server.state],
        ["Game State", server.gameState || "--"],
        ["Pod Name", server.podName],
        ["Pod IP", server.podIp || "--"],
        ["Port", `${server.port}`],
        ["Template", server.templateVersion],
      ]
    : [];
  const statsServer = runtimeSnapshot ?? server;

  const totalPlayerPages = getPaginatedTotalPages(playerPage);

  return (
    <div className="space-y-8">
      <AppToasts
        items={[
          ...(errorMessage
            ? [
                {
                  id: "server-detail-error",
                  message: errorMessage,
                  onDismiss: () => setErrorMessage(null),
                  tone: "error" as const,
                },
              ]
            : []),
          ...(successMessage
            ? [
                {
                  id: "server-detail-success",
                  message: successMessage,
                  onDismiss: () => setSuccessMessage(null),
                  tone: "success" as const,
                },
              ]
            : []),
        ]}
      />

      <motion.section
        initial={{ y: 12, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35, ease: "easeOut" }}
        className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between"
      >
        <div>
          <Link
            to="/servers"
            className="inline-flex items-center gap-2 text-sm font-medium text-slate-400 transition-colors hover:text-primary"
          >
            <FiArrowLeft className="h-4 w-4" />
            Back to servers
          </Link>
          <h2 className="mt-3 flex items-center gap-2 text-lg font-bold text-white">
            <FiServer className="h-5 w-5 text-primary" />
            {server?.displayName || serverId || "Server Details"}
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            General runtime information, player list, and direct server controls.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          {server && (
            <Link
              to={`/groups/${encodeURIComponent(server.group)}`}
              className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200"
            >
              <FiExternalLink className="h-4 w-4" />
              Open Group Config
            </Link>
          )}
        </div>
      </motion.section>

      <motion.section
        initial={{ y: 16, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
        className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]"
      >
        <div className="flex h-full flex-col rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
          <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
              General Information
            </h3>
          </div>

          <div className="p-6">
            {server ? (
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {infoRows.map(([label, value]) => (
                  <div
                    key={label}
                    className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3"
                  >
                    <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                      {label}
                    </p>
                    {label === "State" ? (
                      <span
                        className={`mt-2 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${getServerStateTone(
                          value
                        )}`}
                      >
                        <span className="h-1 w-1 rounded-full bg-current" />
                        {value}
                      </span>
                    ) : (
                      <p
                        className={`mt-1.5 font-medium text-slate-200 ${
                          label === "Server ID" || label === "Pod Name"
                            ? "break-all font-mono text-xs"
                            : "break-all text-sm"
                        }`}
                      >
                        {value}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-400">Loading server information...</p>
            )}
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
            <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                Runtime Stats
              </h3>
            </div>

            <div className="grid grid-cols-1 gap-3 p-6 md:grid-cols-2">
              <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                  TPS
                </p>
                <p
                  className={`mt-1.5 text-sm font-semibold ${
                    statsServer && statsServer.tps >= 18 ? "text-emerald-300" : "text-amber-300"
                  }`}
                >
                  {statsServer ? formatTps(statsServer.tps) : "--"}
                </p>
              </div>
              <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                  Players
                </p>
                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                  {statsServer
                    ? `${statsServer.playerCount} / ${statsServer.maxPlayers}`
                    : "--"}
                </p>
              </div>
              <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                  Memory
                </p>
                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                  {statsServer ? formatMemoryMb(statsServer.memoryUsedMb) : "--"}
                </p>
              </div>
              <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                  Last Heartbeat
                </p>
                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                  {statsServer ? formatDateTime(statsServer.lastHeartbeat) : "--"}
                </p>
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
            <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                Server Actions
              </h3>
            </div>

            <div className="p-6">
              {server ? (
                <div className="flex flex-wrap items-center justify-center gap-1.5">
                  <ServerActionButtons
                    serverId={server.id}
                    serverType={server.type}
                    activeActionKey={activeActionKey}
                    onAction={handleServerAction}
                    iconOnly={false}
                  />
                  <button
                    type="button"
                    onClick={() => setIsCommandModalOpen(true)}
                    disabled={!canExecuteCommands}
                    className="app-button-field button-hover-lift button-shadow-neutral inline-flex h-8 w-auto items-center justify-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-medium text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                    title={
                      canExecuteCommands
                        ? "Execute Command"
                        : "Only admin and service accounts can execute commands."
                    }
                  >
                    <FiTerminal className="h-4 w-4" />
                    Execute Command
                  </button>
                </div>
              ) : (
                <p className="text-sm text-slate-400">Loading available actions...</p>
              )}
            </div>
          </div>
        </div>
      </motion.section>

      <motion.section
        initial={{ y: 20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35, ease: "easeOut", delay: 0.09 }}
        className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
      >
        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center">
              <h3 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-400">
                <FiUsers className="h-4 w-4 text-primary" />
                Connected Players
              </h3>
            </div>
            <div className="flex items-center justify-between gap-3 sm:justify-end">
              <span className="text-xs text-slate-500">
                {playerPage.totalItems} online player{playerPage.totalItems === 1 ? "" : "s"}
              </span>
              <TableRefreshButton
                onClick={() => {
                  void loadServerDetails(false);
                }}
                isRefreshing={isPlayerPageLoading}
                label="Refresh connected players"
              />
            </div>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-800/30">
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Player
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Group
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Connected To Network
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  UUID
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {isLoading || isPlayerPageLoading ? (
                <tr>
                  <td
                    colSpan={4}
                    className="px-6 py-10 text-center text-sm text-slate-400"
                  >
                    Loading players...
                  </td>
                </tr>
              ) : playerPage.items.length === 0 ? (
                <tr>
                  <td
                    colSpan={4}
                    className="px-6 py-10 text-center text-sm text-slate-400"
                  >
                    No players are currently connected to this server.
                  </td>
                </tr>
              ) : (
                playerPage.items.map((player) => (
                  <tr key={player.uuid} className="transition-colors hover:bg-slate-800/20">
                    <td className="px-6 py-4">
                      <div>
                        <p className="text-sm font-semibold text-white">{player.name}</p>
                        <p className="text-xs text-slate-500">
                          {player.proxyDisplayName || player.proxyId || "Unknown proxy"}
                        </p>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-300">
                      {player.groupId || "--"}
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-400">
                      {formatDateTime(player.connectedAt)}
                    </td>
                    <td className="px-6 py-4 font-mono text-sm text-slate-300">
                      {player.uuid}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="flex flex-col gap-3 border-t border-slate-800 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-sm text-slate-400">
            Page {playerPage.page + 1} of {totalPlayerPages}
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={playerPageIndex === 0 || isPlayerPageLoading}
              onClick={() => setPlayerPageIndex((value) => Math.max(0, value - 1))}
              className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <FiChevronLeft className="h-4 w-4" />
              Previous
            </button>
            <button
              type="button"
              disabled={!getPaginatedHasNext(playerPage) || isPlayerPageLoading}
              onClick={() => setPlayerPageIndex((value) => value + 1)}
              className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              Next
              <FiChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </motion.section>

      {server && canExecuteCommands ? (
        <ExecuteCommandModal
          isOpen={isCommandModalOpen}
          onClose={() => setIsCommandModalOpen(false)}
          getAccessToken={getValidAccessToken}
          onSuccess={setSuccessMessage}
          target={server.id}
          targetType="server"
          title="Execute Server Command"
          description="Send a console command directly to this server instance."
          submitLabel="Send To Server"
        />
      ) : null}
    </div>
  );
};

export default ServerDetailsPage;
