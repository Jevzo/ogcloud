import { useCallback, useEffect, useState } from "react";
import { motion } from "motion/react";
import {
  FiChevronLeft,
  FiChevronRight,
  FiPlay,
  FiServer,
  FiTerminal,
} from "react-icons/fi";
import { useNavigate } from "react-router";

import AppSelect from "@/components/AppSelect";
import AppToasts from "@/components/AppToasts";
import DeployServerModal from "@/components/DeployServerModal";
import ExecuteCommandModal from "@/components/ExecuteCommandModal";
import ServerActionButtons from "@/components/ServerActionButtons";
import TableRefreshButton from "@/components/TableRefreshButton";
import { listGroups, listServers } from "@/lib/api";
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
import type { CommandTargetType } from "@/types/command";
import type { PaginatedResponse } from "@/types/dashboard";
import type { GroupListItem } from "@/types/dashboard";
import type { ServerActionKind, ServerRecord } from "@/types/server";

const SERVER_PAGE_SIZE = 10;
const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_SERVER_PAGE: PaginatedResponse<ServerRecord> = {
  items: [],
  page: 0,
  size: SERVER_PAGE_SIZE,
  totalItems: 0,
};

const ServersPage = () => {
  const navigate = useNavigate();
  const session = useAuthStore((state) => state.session);
  const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
  const canExecuteCommands = hasAdminAccess(session?.user.role);

  const [serverPage, setServerPage] =
    useState<PaginatedResponse<ServerRecord>>(EMPTY_SERVER_PAGE);
  const [groups, setGroups] = useState<GroupListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingGroups, setIsLoadingGroups] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [groupFilter, setGroupFilter] = useState("");
  const [activeActionKey, setActiveActionKey] = useState<string | null>(null);
  const [isDeployModalOpen, setIsDeployModalOpen] = useState(false);
  const [isCommandModalOpen, setIsCommandModalOpen] = useState(false);

  const getValidAccessToken = useCallback(async () => {
    const nextSession = await refreshIfNeeded();

    if (!nextSession) {
      throw new Error("Your session expired. Please sign in again.");
    }

    return nextSession.accessToken;
  }, [refreshIfNeeded]);

  const loadServersPage = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setIsLoading(true);
    }

    try {
      const accessToken = await getValidAccessToken();
      const nextPage = await listServers(accessToken, {
        group: groupFilter || undefined,
        page: currentPage,
        size: SERVER_PAGE_SIZE,
      });

      setServerPage(nextPage);
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to load servers."
      );
    } finally {
      setIsLoading(false);
    }
  }, [currentPage, getValidAccessToken, groupFilter]);

  useEffect(() => {
    let active = true;

    const loadGroupsForFilter = async () => {
      try {
        const accessToken = await getValidAccessToken();
        const nextGroups = await listGroups(accessToken);

        if (!active) {
          return;
        }

        setGroups(nextGroups);
      } catch (error) {
        if (!active) {
          return;
        }

        setErrorMessage(
          error instanceof Error ? error.message : "Unable to load groups."
        );
      } finally {
        if (active) {
          setIsLoadingGroups(false);
        }
      }
    };

    void loadGroupsForFilter();

    return () => {
      active = false;
    };
  }, [getValidAccessToken]);

  useEffect(() => {
    let active = true;

    const runLoad = async (showLoading = true) => {
      if (!active) {
        return;
      }

      await loadServersPage(showLoading);
    };

    void runLoad(true);

    const intervalId = window.setInterval(() => {
      void runLoad(false);
    }, REFRESH_INTERVAL_MS);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, [loadServersPage]);

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

  const handleGroupFilterChange = (nextValue: string) => {
    setCurrentPage(0);
    setGroupFilter(nextValue);
  };

  const handleServerAction = async (
    serverId: string,
    action: ServerActionKind
  ) => {
    const actionKey = `${serverId}:${action}`;
    setActiveActionKey(actionKey);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      await runServerAction(accessToken, serverId, action);
      setSuccessMessage(getServerActionSuccessMessage(serverId, action));
      await loadServersPage(false);
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

  const totalPages = getPaginatedTotalPages(serverPage);
  const hasServers = serverPage.items.length > 0;
  const hasGroupFilter = groupFilter.trim().length > 0;
  const commandTarget = hasGroupFilter ? groupFilter : "all";
  const commandTargetType: CommandTargetType = hasGroupFilter ? "group" : "all";
  const commandButtonLabel = hasGroupFilter
    ? `Command ${groupFilter}`
    : "Command All Servers";
  const commandTitle = hasGroupFilter ? "Execute Group Command" : "Execute Network Command";
  const commandDescription = hasGroupFilter
    ? `Send a console command to every running server in ${groupFilter}.`
    : "Send a console command to every running server across the network.";
  const commandSubmitLabel = hasGroupFilter ? "Send To Group" : "Send To All Servers";

  return (
    <div className="space-y-8">
      <AppToasts
        items={[
          ...(errorMessage
            ? [
                {
                  id: "servers-error",
                  message: errorMessage,
                  onDismiss: () => setErrorMessage(null),
                  tone: "error" as const,
                },
              ]
            : []),
          ...(successMessage
            ? [
                {
                  id: "servers-success",
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
        className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between"
      >
        <div>
          <h2 className="flex items-center gap-2 text-lg font-bold text-white">
            <FiServer className="h-5 w-5 text-primary" />
            Servers
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Browse all server instances with backend pagination and direct runtime
            controls.
          </p>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="flex items-center gap-2">
            <div className="min-w-48">
              <AppSelect
                value={groupFilter}
                onChangeValue={handleGroupFilterChange}
                disabled={isLoadingGroups}
              >
                <option value="">All groups</option>
                {groups.map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.id}
                  </option>
                ))}
              </AppSelect>
            </div>

            <button
              type="button"
              onClick={() => setIsCommandModalOpen(true)}
              disabled={!canExecuteCommands}
              className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
              title={
                canExecuteCommands
                  ? commandButtonLabel
                  : "Only admin and service accounts can execute commands."
              }
            >
              <FiTerminal className="h-4 w-4" />
              {commandButtonLabel}
            </button>

            <button
              type="button"
              onClick={() => setIsDeployModalOpen(true)}
              className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-semibold text-slate-950"
            >
              <FiPlay className="h-4 w-4" />
              Deploy New
            </button>
          </div>
        </div>
      </motion.section>

      <motion.section
        initial={{ y: 16, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
        className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
      >
        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                Server Instances
              </h3>
            </div>
            <div className="flex items-center justify-between gap-3 sm:justify-end">
              <span className="text-xs text-slate-500">
                {serverPage.totalItems} total instance
                {serverPage.totalItems === 1 ? "" : "s"}
              </span>
              <TableRefreshButton
                onClick={() => {
                  void loadServersPage(false);
                }}
                isRefreshing={isLoading}
                label="Refresh server table"
              />
            </div>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-800/30">
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Instance ID
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Name
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Group
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  State
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  TPS
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Players
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Memory
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Last Heartbeat
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase text-slate-500">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {isLoading ? (
                <tr>
                  <td
                    colSpan={9}
                    className="px-6 py-10 text-center text-sm text-slate-400"
                  >
                    Loading servers...
                  </td>
                </tr>
              ) : !hasServers ? (
                <tr>
                  <td
                    colSpan={9}
                    className="px-6 py-10 text-center text-sm text-slate-400"
                  >
                    No servers matched this filter.
                  </td>
                </tr>
              ) : (
                serverPage.items.map((server) => (
                  <tr
                    key={server.id}
                    onClick={() => navigate(`/servers/${encodeURIComponent(server.id)}`)}
                    className="cursor-pointer transition-colors hover:bg-slate-800/30"
                  >
                    <td className="px-6 py-4">
                      <span className="font-mono text-sm text-slate-200">
                        {server.id}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div>
                        <p className="text-sm font-semibold text-white">
                          {server.displayName}
                        </p>
                        <p className="text-xs text-slate-500">{server.type}</p>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-300">{server.group}</td>
                    <td className="px-6 py-4">
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${getServerStateTone(
                          server.state
                        )}`}
                      >
                        <span className="h-1 w-1 rounded-full bg-current" />
                        {server.state}
                      </span>
                    </td>
                    <td
                      className={`px-6 py-4 font-mono text-sm font-bold ${
                        server.tps >= 18 ? "text-emerald-400" : "text-amber-300"
                      }`}
                    >
                      {formatTps(server.tps)}
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-300">
                      {server.playerCount} / {server.maxPlayers}
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-300">
                      {formatMemoryMb(server.memoryUsedMb)}
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-400">
                      {formatDateTime(server.lastHeartbeat)}
                    </td>
                    <td
                      className="px-6 py-4 text-right"
                      onClick={(event) => event.stopPropagation()}
                    >
                      <ServerActionButtons
                        serverId={server.id}
                        serverType={server.type}
                        activeActionKey={activeActionKey}
                        onAction={handleServerAction}
                      />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="flex flex-col gap-3 border-t border-slate-800 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-sm text-slate-400">
            Page {serverPage.page + 1} of {totalPages}
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={currentPage === 0 || isLoading}
              onClick={() => setCurrentPage((value) => Math.max(0, value - 1))}
              className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <FiChevronLeft className="h-4 w-4" />
              Previous
            </button>
            <button
              type="button"
              disabled={!getPaginatedHasNext(serverPage) || isLoading}
              onClick={() => setCurrentPage((value) => value + 1)}
              className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              Next
              <FiChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </motion.section>

      <DeployServerModal
        isOpen={isDeployModalOpen}
        onClose={() => setIsDeployModalOpen(false)}
        getAccessToken={getValidAccessToken}
        onSuccess={setSuccessMessage}
        onRequested={() => loadServersPage(false)}
      />
      {canExecuteCommands ? (
        <ExecuteCommandModal
          isOpen={isCommandModalOpen}
          onClose={() => setIsCommandModalOpen(false)}
          getAccessToken={getValidAccessToken}
          onSuccess={setSuccessMessage}
          target={commandTarget}
          targetType={commandTargetType}
          title={commandTitle}
          description={commandDescription}
          submitLabel={commandSubmitLabel}
        />
      ) : null}
    </div>
  );
};

export default ServersPage;
