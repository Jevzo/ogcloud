import type {
  GroupOverview,
  GroupView,
  GroupViewType,
} from "@ogcloud/api-client";
import {
  useGroupDeleted,
  useGroupList,
  useOverviewGet,
} from "@ogcloud/api-client/react";
import {
  Button,
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  FilterBar,
  Input,
  PageHeader,
  Segmented,
  Select,
} from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import { Layers, Plus, Undo2 } from "lucide-react";
import { useMemo, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { monoColumn } from "../../columns.tsx";
import { DetailRow } from "../../details.tsx";
import { loadFailure } from "../../errors.ts";
import { useHeld } from "../../held.ts";
import { Load, ServerCount } from "../../load.tsx";
import {
  placeholderGroup,
  placeholderGroupOverview,
} from "../../placeholders.ts";
import { RecordDrawer } from "../../record-drawer.tsx";
import { useGroupActions } from "./group-actions.tsx";
import {
  GROUP_STATUSES,
  GroupLink,
  type GroupStatus,
  GroupStatusBadge,
  statusOf,
} from "./group-status.tsx";

/** A group's settings with what runs of it right now. */
interface GroupRow {
  view: GroupView;
  live: GroupOverview;
}

const columns = columnsFor<GroupRow>();
const PLACEHOLDER_ROW: GroupRow = {
  view: placeholderGroup,
  live: placeholderGroupOverview,
};
const NO_LOAD = { running: 0, pending: 0, players: 0, capacity: 0 };
const TYPES: readonly GroupViewType[] = ["STATIC", "DYNAMIC", "PROXY"];
const ANY = "all";
type StateFilter = GroupStatus | typeof ANY;
type TypeFilter = GroupViewType | typeof ANY;
const STATE_FILTERS: readonly StateFilter[] = [ANY, ...GROUP_STATUSES];

/** Every group with its runtime, scaling range and live load, narrowed by name, state and type; and the groups deleted recently. */
export function GroupsPage() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const groups = useGroupList();
  const overview = useOverviewGet();
  const deleted = useGroupDeleted();
  const actions = useGroupActions();
  const [opened, setOpened] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [state, setState] = useState<StateFilter>(ANY);
  const [type, setType] = useState<TypeFilter>(ANY);

  const live = new Map(
    (overview.data?.data.groups ?? []).map((group) => [group.id, group]),
  );
  const rows: GroupRow[] = (groups.data?.data ?? []).map((view) => ({
    view,
    live: live.get(view.id) ?? {
      id: view.id,
      type: view.type,
      maintenance: view.settings.maintenance,
      crashLoopPaused: false,
      ...NO_LOAD,
    },
  }));

  const query = search.trim().toLowerCase();
  const shown = rows.filter(
    (row) =>
      (query === "" || row.view.id.includes(query)) &&
      (state === ANY || statusOf(row.live) === state) &&
      (type === ANY || row.view.type === type),
  );
  const filtered = query !== "" || state !== ANY || type !== ANY;

  const groupColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("view.id", {
          header: () => t("groups.columns.group"),
          cell: (cell) => <GroupLink id={cell.getValue()} />,
        }),
        columns.accessor("view.type", {
          header: () => t("groups.columns.type"),
          cell: (cell) => t(`groups.type.${cell.getValue()}`),
        }),
        monoColumn(
          columns,
          "profile",
          t("groups.columns.profile"),
          (row) => row.view.settings.profileId,
        ),
        columns.accessor("live.running", {
          header: () => t("groups.columns.servers"),
          meta: { numeric: true },
          cell: (cell) => (
            <ServerCount
              running={cell.getValue()}
              pending={cell.row.original.live.pending}
            />
          ),
        }),
        columns.accessor("live.players", {
          header: () => t("groups.columns.players"),
          meta: { numeric: true },
          cell: (cell) => (
            <Load
              value={cell.getValue()}
              max={cell.row.original.live.capacity}
            />
          ),
        }),
        columns.accessor("view.settings.scaling", {
          header: () => t("groups.columns.scaling"),
          enableSorting: false,
          meta: { numeric: true },
          cell: (cell) => (
            <span className="text-text-2">
              {t("groups.range", {
                min: cell.getValue().minOnline,
                max: cell.getValue().maxInstances,
              })}
            </span>
          ),
        }),
        columns.display({
          id: "status",
          header: () => t("groups.columns.state"),
          cell: (cell) => <GroupStatusBadge group={cell.row.original.live} />,
        }),
      ]),
    [t],
  );

  const removed = deleted.data?.data ?? [];
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("nav.groups")}
        actions={
          actions.canWrite ? (
            <Button
              kind="primary"
              icon={Plus}
              onClick={() => navigate({ to: "/groups/new" })}
            >
              {t("groups.create.title")}
            </Button>
          ) : undefined
        }
      />
      <FilterBar
        onClear={
          filtered
            ? () => {
                setSearch("");
                setState(ANY);
                setType(ANY);
              }
            : undefined
        }
      >
        <Segmented
          label={t("groups.filters.state")}
          options={STATE_FILTERS.map((value) => ({
            value,
            label: t(`groups.filters.states.${value}`),
          }))}
          value={state}
          onValueChange={setState}
        />
        <Select
          className="w-40"
          aria-label={t("groups.filters.type")}
          options={[
            { value: ANY, label: t("groups.filters.allTypes") },
            ...TYPES.map((value) => ({
              value,
              label: t(`groups.type.${value}`),
            })),
          ]}
          value={type}
          onValueChange={setType}
        />
        <Input
          className="w-64"
          type="search"
          aria-label={t("groups.filters.search")}
          placeholder={t("groups.filters.search")}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </FilterBar>
      <Card flush>
        <DataTable
          label={t("nav.groups")}
          data={shown}
          failure={loadFailure(groups)}
          columns={groupColumns}
          getRowId={(row) => row.view.id}
          placeholder={PLACEHOLDER_ROW}
          actions={(row) => actions.actionsFor(row.live)}
          onRowOpen={(row) => setOpened(row.view.id)}
          loading={groups.isPending}
          empty={
            rows.length === 0 ? (
              <EmptyState
                icon={Layers}
                title={t("overview.groups.empty")}
                description={t("overview.groups.emptyDescription")}
              />
            ) : (
              <EmptyState
                icon={Layers}
                title={t("groups.filters.empty")}
                description={t("groups.filters.emptyDescription")}
              />
            )
          }
        />
      </Card>
      <GroupDrawer
        row={rows.find((row) => row.view.id === opened) ?? null}
        onClose={() => setOpened(null)}
      />
      {removed.length === 0 ? null : (
        <Card title={t("groups.deleted.title")}>
          <p className="mb-2 text-small text-text-3">
            {t("groups.deleted.description")}
          </p>
          <ul className="flex flex-col">
            {removed.map(({ group, deletedAt }) => (
              <li
                key={group.id}
                className="flex h-row items-center gap-3 border-border-subtle border-t first:border-t-0"
              >
                <span className="font-label">{group.id}</span>
                <span className="text-text-2">
                  {t(`groups.type.${group.type}`)}
                </span>
                <span className="flex-1 text-text-3">
                  <Trans
                    t={t}
                    i18nKey="groups.deleted.when"
                    components={{ ago: <Ago at={deletedAt} className="" /> }}
                  />
                </span>
                {actions.canWrite ? (
                  <Button
                    size="sm"
                    icon={Undo2}
                    onClick={() => actions.request("restore", group.id)}
                  >
                    {t("groups.actions.restore")}
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        </Card>
      )}
      {actions.dialogs}
    </div>
  );
}

/** A group's key facts beside the list, and the way to its page (D-0231). */
function GroupDrawer({
  row: current,
  onClose,
}: {
  row: GroupRow | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  // The drawer slides out still showing what it showed.
  const row = useHeld(current);
  if (row === null) return null;
  const { view, live } = row;
  return (
    <RecordDrawer
      open={current !== null}
      title={view.id}
      badges={<GroupStatusBadge group={live} />}
      onClose={onClose}
      onOpenPage={() =>
        void navigate({ to: "/groups/$groupId", params: { groupId: view.id } })
      }
    >
      <DetailRow label={t("groups.columns.type")}>
        {t(`groups.type.${view.type}`)}
      </DetailRow>
      <DetailRow label={t("groups.columns.profile")}>
        <span className="font-mono text-mono">{view.settings.profileId}</span>
      </DetailRow>
      <DetailRow label={t("groups.columns.servers")}>
        <ServerCount running={live.running} pending={live.pending} />
      </DetailRow>
      <DetailRow label={t("groups.columns.players")}>
        <Load value={live.players} max={live.capacity} />
      </DetailRow>
      <DetailRow label={t("groups.columns.scaling")}>
        {t("groups.range", {
          min: view.settings.scaling.minOnline,
          max: view.settings.scaling.maxInstances,
        })}
      </DetailRow>
      <DetailRow label={t("groups.create.memory")}>
        <span className="font-mono text-mono">
          {view.settings.resources.memoryLimit}
        </span>
      </DetailRow>
    </RecordDrawer>
  );
}
