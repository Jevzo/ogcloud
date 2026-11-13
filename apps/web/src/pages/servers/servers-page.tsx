import type { ServerView, ServerViewState } from "@ogcloud/api-client";
import { useOverviewGet, useServerList } from "@ogcloud/api-client/react";
import {
  Button,
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  Input,
  PageHeader,
  Segmented,
} from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import { Plus, Server, SquareTerminal } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { loadFailure } from "../../errors.ts";
import { Load } from "../../load.tsx";
import { placeholderServer } from "../../placeholders.ts";
import { ALL_GROUPS, GroupFilter } from "../groups/group-filter.tsx";
import { CommandDialog } from "./command-dialog.tsx";
import { RequestServerDialog } from "./request-server-dialog.tsx";
import { useServerActions } from "./server-actions.tsx";
import { ServerStateBadge } from "./server-state.tsx";

// Player counts change with heartbeats, which send no events (D-0105).
const REFRESH_MS = 10_000;
const PHASES = {
  all: [],
  running: ["RUNNING"],
  starting: ["REQUESTED", "PREPARING", "STARTING"],
  draining: ["DRAINING", "STOPPING"],
} as const satisfies Record<string, readonly ServerViewState[]>;
type Phase = keyof typeof PHASES;

const columns = columnsFor<ServerView>();

/** Every live server, filtered by phase, group and name, with its actions in each row. */
export function ServersPage() {
  const { t } = useTranslation("web");
  const servers = useServerList(undefined, {
    query: { refetchInterval: REFRESH_MS },
  });
  const overview = useOverviewGet();
  const groupIds = overview.data?.data.groups.map((group) => group.id) ?? [];
  const actions = useServerActions(groupIds);
  const [phase, setPhase] = useState<Phase>("all");
  const [group, setGroup] = useState(ALL_GROUPS);
  const [search, setSearch] = useState("");
  const [requesting, setRequesting] = useState(false);
  const [commanding, setCommanding] = useState(false);

  const all = servers.data?.data ?? [];
  const query = search.trim().toLowerCase();
  const states: readonly ServerViewState[] = PHASES[phase];
  const shown = all.filter(
    (server) =>
      (states.length === 0 || states.includes(server.state)) &&
      (group === ALL_GROUPS || server.groupId === group) &&
      (query === "" || server.displayName.includes(query)),
  );

  const serverColumns = useMemo(() => {
    return columns.columns([
      columns.accessor("displayName", {
        header: () => t("servers.columns.server"),
        cell: (cell) => (
          <Link
            to="/servers/$serverId"
            params={{ serverId: cell.row.original.id }}
            className="font-label font-mono hover:underline"
          >
            {cell.getValue()}
          </Link>
        ),
      }),
      columns.accessor("groupId", {
        header: () => t("servers.columns.group"),
        cell: (cell) => <span className="text-text-2">{cell.getValue()}</span>,
      }),
      columns.accessor("state", {
        header: () => t("servers.columns.state"),
        cell: (cell) => <ServerStateBadge state={cell.getValue()} />,
      }),
      columns.accessor("players", {
        header: () => t("servers.columns.players"),
        meta: { numeric: true },
        cell: (cell) => (
          <Load value={cell.getValue()} max={cell.row.original.maxPlayers} />
        ),
      }),
      columns.accessor("gameState", {
        header: () => t("servers.columns.game"),
        cell: (cell) => {
          const game = cell.getValue();
          return (
            <span className="text-text-2">
              {game == null ? "—" : t(`servers.game.${game}`)}
            </span>
          );
        },
      }),
      columns.accessor("startedAt", {
        header: () => t("servers.columns.started"),
        cell: (cell) => <Ago at={cell.getValue()} />,
      }),
      columns.accessor("lastHeartbeatAt", {
        header: () => t("servers.columns.heartbeat"),
        cell: (cell) => <Ago at={cell.getValue()} />,
      }),
    ]);
  }, [t]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("nav.servers")}
        actions={
          actions.can.command ? (
            <>
              <Button icon={SquareTerminal} onClick={() => setCommanding(true)}>
                {t("servers.actions.command")}
              </Button>
              <Button
                kind="primary"
                icon={Plus}
                onClick={() => setRequesting(true)}
              >
                {t("servers.request.submit")}
              </Button>
            </>
          ) : undefined
        }
      />
      <div className="flex flex-wrap items-center gap-2">
        <Segmented
          label={t("servers.filters.phase")}
          options={(Object.keys(PHASES) as Phase[]).map((value) => ({
            value,
            label: t(`servers.filters.${value}`),
          }))}
          value={phase}
          onValueChange={setPhase}
        />
        <GroupFilter
          groupIds={groupIds}
          value={group}
          onValueChange={setGroup}
        />
        <Input
          className="w-64"
          type="search"
          aria-label={t("servers.filters.search")}
          placeholder={t("servers.filters.search")}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>
      <Card flush>
        <DataTable
          label={t("nav.servers")}
          data={shown}
          failure={loadFailure(servers)}
          columns={serverColumns}
          getRowId={(server) => server.id}
          placeholder={placeholderServer}
          actions={actions.actionsFor}
          loading={servers.isPending}
          maxHeight="calc(100vh - 14rem)"
          empty={
            <EmptyState
              icon={Server}
              title={
                all.length === 0
                  ? t("servers.empty.none")
                  : t("servers.empty.filtered")
              }
              description={
                all.length === 0
                  ? t("servers.empty.noneDescription")
                  : t("servers.empty.filteredDescription")
              }
            />
          }
        />
      </Card>
      {actions.dialogs}
      <RequestServerDialog
        open={requesting}
        onOpenChange={setRequesting}
        groupIds={groupIds}
      />
      <CommandDialog
        open={commanding}
        onOpenChange={setCommanding}
        target={{ kind: "choose", groupIds }}
      />
    </div>
  );
}
