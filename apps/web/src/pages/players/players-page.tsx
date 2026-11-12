import type { OnlinePlayerView, PlayerView } from "@ogcloud/api-client";
import {
  getPlayerKnownQueryKey,
  getPlayerOnlineQueryKey,
  usePlayerKnownHook,
  usePlayerOnlineHook,
} from "@ogcloud/api-client/react";
import {
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  Input,
  PageHeader,
  Segmented,
} from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import { Users } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { TYPING_PAUSE_MS, useDebounced } from "../../debounce.ts";
import { loadFailure } from "../../errors.ts";
import { useFormat } from "../../format.ts";
import { LoadMore } from "../../load-more.tsx";
import { PAGE_SIZE, usePaged } from "../../paged.ts";
import {
  placeholderOnlinePlayer,
  placeholderPlayer,
} from "../../placeholders.ts";
import { useServerNames } from "../servers/server-names.ts";
import { type PlayerRef, usePlayerActions } from "./player-actions.tsx";

type View = "online" | "everyone";
const REFRESH_MS = 10_000;

interface Named {
  uuid: string;
  name: string;
}

/** The player's name, linking to their page; sortable by name. */
function nameColumn<T extends Named>(
  columns: ReturnType<typeof columnsFor<T>>,
  header: string,
) {
  return columns.accessor((player) => player.name, {
    id: "name",
    header: () => header,
    cell: (cell) => (
      <Link
        to="/players/$playerId"
        params={{ playerId: cell.row.original.uuid }}
        className="font-label hover:underline"
      >
        {cell.getValue()}
      </Link>
    ),
  });
}

interface PlayerTableProps<T extends Named> {
  label: string;
  paged: ReturnType<typeof usePaged<T>>;
  rows: readonly T[];
  placeholder: T;
  columns: Parameters<typeof DataTable<T>>[0]["columns"];
  toRef: (row: T) => PlayerRef;
  actions: ReturnType<typeof usePlayerActions>;
  emptyTitle: string;
  emptyDescription: string;
}

/** A page-wise loaded list of players with their row actions. */
function PlayerTable<T extends Named>({
  label,
  paged,
  rows,
  placeholder,
  columns,
  toRef,
  actions,
  emptyTitle,
  emptyDescription,
}: PlayerTableProps<T>) {
  return (
    <>
      <DataTable
        label={label}
        data={rows}
        failure={loadFailure(paged)}
        columns={columns}
        getRowId={(player) => player.uuid}
        placeholder={placeholder}
        actions={(player) => actions.actionsFor(toRef(player))}
        loading={paged.pending}
        maxHeight="calc(100vh - 16rem)"
        empty={
          <EmptyState
            icon={Users}
            title={emptyTitle}
            description={emptyDescription}
          />
        }
      />
      <LoadMore paged={paged} />
    </>
  );
}

/** Who is online now, and everyone the network has seen, searchable by name. */
export function PlayersPage() {
  const { t } = useTranslation("web");
  const [view, setView] = useState<View>("online");
  const [search, setSearch] = useState("");
  // The known players are searched by the API, so it is asked once typing pauses.
  const settled = useDebounced(search.trim(), TYPING_PAUSE_MS);
  const actions = usePlayerActions();

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("nav.players")} />
      <div className="flex flex-wrap items-center gap-2">
        <Segmented
          label={t("players.view")}
          options={[
            { value: "online", label: t("players.online") },
            { value: "everyone", label: t("players.everyone") },
          ]}
          value={view}
          onValueChange={setView}
        />
        <Input
          className="w-64"
          type="search"
          aria-label={t("players.search")}
          placeholder={t("players.search")}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>
      <Card flush>
        {view === "online" ? (
          <OnlineTable search={settled} actions={actions} />
        ) : (
          <KnownTable search={settled} actions={actions} />
        )}
      </Card>
      {actions.dialogs}
    </div>
  );
}

const onlineColumns = columnsFor<OnlinePlayerView>();

function OnlineTable({
  search,
  actions,
}: {
  search: string;
  actions: ReturnType<typeof usePlayerActions>;
}) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const servers = useServerNames();
  const fetchOnline = usePlayerOnlineHook();
  // Names are matched by the API, by prefix like the known players' (D-0223).
  const params = search === "" ? {} : { name: search };
  const online = usePaged(
    getPlayerOnlineQueryKey(params),
    async (cursor) =>
      (
        await fetchOnline({
          ...params,
          limit: PAGE_SIZE,
          ...(cursor === undefined ? {} : { cursor }),
        })
      ).data,
    REFRESH_MS,
  );

  const columns = useMemo(
    () =>
      onlineColumns.columns([
        nameColumn(onlineColumns, t("players.columns.player")),
        onlineColumns.accessor("session.permissionGroupId", {
          header: () => t("players.columns.rank"),
          cell: (cell) => (
            <span className="text-text-2">{cell.getValue()}</span>
          ),
        }),
        onlineColumns.accessor("session.serverId", {
          header: () => t("players.columns.server"),
          cell: (cell) => {
            const id = cell.getValue();
            return (
              <span className="font-mono text-mono text-text-2">
                {id == null ? "—" : (servers.get(id) ?? id.slice(0, 8))}
              </span>
            );
          },
        }),
        onlineColumns.accessor("session.proxyId", {
          header: () => t("players.columns.proxy"),
          cell: (cell) => (
            <span className="font-mono text-mono text-text-2">
              {servers.get(cell.getValue()) ?? cell.getValue().slice(0, 8)}
            </span>
          ),
        }),
        onlineColumns.accessor("session.connectedAt", {
          header: () => t("players.columns.connected"),
          cell: (cell) => <Ago at={cell.getValue()} />,
        }),
      ]),
    [t, format, servers],
  );

  return (
    <PlayerTable
      label={t("players.online")}
      paged={online}
      rows={online.items}
      placeholder={placeholderOnlinePlayer}
      columns={columns}
      toRef={(player) => ({
        uuid: player.uuid,
        name: player.name,
        permissionGroupId: player.session.permissionGroupId,
        online: true,
      })}
      actions={actions}
      emptyTitle={t("players.empty.online")}
      emptyDescription={t("players.empty.onlineDescription")}
    />
  );
}

const knownColumns = columnsFor<PlayerView>();

function KnownTable({
  search,
  actions,
}: {
  search: string;
  actions: ReturnType<typeof usePlayerActions>;
}) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const fetchKnown = usePlayerKnownHook();
  const params = search === "" ? {} : { name: search };
  const known = usePaged(
    getPlayerKnownQueryKey(params),
    async (cursor) =>
      (
        await fetchKnown({
          ...params,
          limit: PAGE_SIZE,
          ...(cursor === undefined ? {} : { cursor }),
        })
      ).data,
  );

  const columns = useMemo(
    () =>
      knownColumns.columns([
        nameColumn(knownColumns, t("players.columns.player")),
        knownColumns.accessor("permissionGroupId", {
          header: () => t("players.columns.rank"),
          cell: (cell) => {
            const expires = cell.row.original.rankExpiresAt;
            return (
              <span className="text-text-2">
                {expires == null
                  ? cell.getValue()
                  : t("players.rankUntil", {
                      group: cell.getValue(),
                      ago: format.ago(new Date(expires), Date.now()),
                    })}
              </span>
            );
          },
        }),
        knownColumns.accessor("lastSeenAt", {
          header: () => t("players.columns.lastSeen"),
          cell: (cell) => <Ago at={cell.getValue()} />,
        }),
        knownColumns.accessor("firstSeenAt", {
          header: () => t("players.columns.firstSeen"),
          cell: (cell) => <Ago at={cell.getValue()} />,
        }),
      ]),
    [t, format],
  );

  return (
    <PlayerTable
      label={t("players.everyone")}
      paged={known}
      rows={known.items}
      placeholder={placeholderPlayer}
      columns={columns}
      toRef={(player) => ({
        uuid: player.uuid,
        name: player.name,
        permissionGroupId: player.permissionGroupId,
        online: false,
      })}
      actions={actions}
      emptyTitle={t("players.empty.known")}
      emptyDescription={t("players.empty.knownDescription")}
    />
  );
}
