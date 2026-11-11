import type { OnlinePlayerView, PlayerView } from "@ogcloud/api-client";
import {
  getPlayerKnownQueryKey,
  getPlayerOnlineQueryKey,
  usePlayerKnownHook,
  usePlayerOnlineHook,
} from "@ogcloud/api-client/react";
import {
  Button,
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
import { useFormat } from "../../format.ts";
import { PAGE_SIZE, usePaged } from "../../paged.ts";
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
        columns={columns}
        getRowId={(player) => player.uuid}
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
  const actions = usePlayerActions();

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("nav.players")}
        description={t("players.description")}
      />
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
      <section className="rounded-card border border-border-subtle bg-surface py-1 shadow-card">
        {view === "online" ? (
          <OnlineTable search={search.trim().toLowerCase()} actions={actions} />
        ) : (
          <KnownTable search={search.trim()} actions={actions} />
        )}
      </section>
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
  const online = usePaged(
    getPlayerOnlineQueryKey(),
    async (cursor) =>
      (
        await fetchOnline({
          limit: PAGE_SIZE,
          ...(cursor === undefined ? {} : { cursor }),
        })
      ).data,
    REFRESH_MS,
  );
  // The API pages by uuid, so names are matched among the pages loaded.
  const shown = online.items.filter(
    (player) => search === "" || player.name.toLowerCase().includes(search),
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
      rows={shown}
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

function LoadMore({
  paged,
}: {
  paged: Pick<
    ReturnType<typeof usePaged>,
    "hasMore" | "loadMore" | "loadingMore"
  >;
}) {
  const { t } = useTranslation("web");
  if (!paged.hasMore) return null;
  return (
    <div className="flex justify-center p-3">
      <Button size="sm" onClick={paged.loadMore} loading={paged.loadingMore}>
        {t("players.loadMore")}
      </Button>
    </div>
  );
}
