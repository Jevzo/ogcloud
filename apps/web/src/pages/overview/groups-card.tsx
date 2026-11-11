import type { GroupOverview } from "@ogcloud/api-client";
import {
  Badge,
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  Meter,
  type RowActions,
  type Tone,
} from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import {
  CircleCheck,
  CircleDashed,
  Construction,
  Layers,
  type LucideIcon,
  OctagonPause,
} from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useFormat } from "../../format.ts";

type GroupStatus = "paused" | "maintenance" | "online" | "idle";

const STATUS_LOOK: Record<GroupStatus, { tone: Tone; icon: LucideIcon }> = {
  paused: { tone: "danger", icon: OctagonPause },
  maintenance: { tone: "warning", icon: Construction },
  online: { tone: "success", icon: CircleCheck },
  idle: { tone: "neutral", icon: CircleDashed },
};

function statusOf(group: GroupOverview): GroupStatus {
  if (group.crashLoopPaused) return "paused";
  if (group.maintenance) return "maintenance";
  return group.running > 0 ? "online" : "idle";
}

const columns = columnsFor<GroupOverview>();

interface GroupsCardProps {
  groups: readonly GroupOverview[];
  actionsFor: ((group: GroupOverview) => RowActions) | undefined;
}

/** Every group with its servers, players against capacity, and state. */
export function GroupsCard({ groups, actionsFor }: GroupsCardProps) {
  const { t } = useTranslation("web");
  const format = useFormat();

  const groupColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("id", {
          header: () => t("groups.columns.group"),
          cell: (cell) => <span className="font-label">{cell.getValue()}</span>,
        }),
        columns.accessor("type", {
          header: () => t("groups.columns.type"),
          cell: (cell) => t(`groups.type.${cell.getValue()}`),
        }),
        columns.accessor("running", {
          header: () => t("groups.columns.servers"),
          cell: (cell) => (
            <span>
              {format.number(cell.getValue())}
              {cell.row.original.pending > 0 ? (
                <span className="text-text-3">
                  {" "}
                  {t("groups.starting", {
                    count: cell.row.original.pending,
                  })}
                </span>
              ) : null}
            </span>
          ),
        }),
        columns.accessor("players", {
          header: () => t("groups.columns.players"),
          cell: (cell) => (
            <span className="inline-flex items-center gap-2.5">
              <Meter value={cell.getValue()} max={cell.row.original.capacity} />
              <span>
                {t("groups.load", {
                  players: format.number(cell.getValue()),
                  capacity: format.number(cell.row.original.capacity),
                })}
              </span>
            </span>
          ),
        }),
        columns.display({
          id: "status",
          header: () => t("groups.columns.state"),
          cell: (cell) => {
            const status = statusOf(cell.row.original);
            return (
              <Badge {...STATUS_LOOK[status]}>
                {t(`groups.status.${status}`)}
              </Badge>
            );
          },
        }),
      ]),
    [t, format],
  );

  return (
    <Card
      flush
      title={t("overview.groups.title")}
      actions={
        <Link
          to="/groups"
          className="font-label text-accent-text text-small hover:underline"
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
