import type { GroupOverview } from "@ogcloud/api-client";
import {
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  type RowActions,
} from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import { Layers } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Load, ServerCount } from "../../load.tsx";
import { placeholderGroupOverview } from "../../placeholders.ts";
import { GroupLink, GroupStatusBadge } from "../groups/group-status.tsx";

const columns = columnsFor<GroupOverview>();

interface GroupsCardProps {
  groups: readonly GroupOverview[];
  actionsFor: (group: GroupOverview) => RowActions;
}

/** Every group with its servers, players against capacity, and state. */
export function GroupsCard({ groups, actionsFor }: GroupsCardProps) {
  const { t } = useTranslation("web");

  const groupColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("id", {
          header: () => t("groups.columns.group"),
          cell: (cell) => <GroupLink id={cell.getValue()} />,
        }),
        columns.accessor("type", {
          header: () => t("groups.columns.type"),
          cell: (cell) => t(`groups.type.${cell.getValue()}`),
        }),
        columns.accessor("running", {
          header: () => t("groups.columns.servers"),
          meta: { numeric: true },
          cell: (cell) => (
            <ServerCount
              running={cell.getValue()}
              pending={cell.row.original.pending}
            />
          ),
        }),
        columns.accessor("players", {
          header: () => t("groups.columns.players"),
          meta: { numeric: true },
          cell: (cell) => (
            <Load value={cell.getValue()} max={cell.row.original.capacity} />
          ),
        }),
        columns.display({
          id: "status",
          header: () => t("groups.columns.state"),
          cell: (cell) => <GroupStatusBadge group={cell.row.original} />,
        }),
      ]),
    [t],
  );

  return (
    <Card
      flush
      title={t("overview.groups.title")}
      actions={
        <Link
          to="/groups"
          className="inline-flex min-h-6 items-center font-label text-accent text-small hover:underline"
        >
          {t("overview.groups.all")}
        </Link>
      }
    >
      <DataTable
        label={t("overview.groups.title")}
        data={groups}
        columns={groupColumns}
        getRowId={(group) => group.id}
        placeholder={placeholderGroupOverview}
        actions={actionsFor}
        empty={
          <EmptyState
            icon={Layers}
            title={t("overview.groups.empty")}
            description={t("overview.groups.emptyDescription")}
          />
        }
      />
    </Card>
  );
}
