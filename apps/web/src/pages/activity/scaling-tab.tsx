import type { ScalingLogView } from "@ogcloud/api-client";
import {
  getLogScalingQueryKey,
  useLogScalingHook,
} from "@ogcloud/api-client/react";
import { Card, columnsFor, DataTable, EmptyState } from "@ogcloud/ui";
import { TrendingUp } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { agoColumn } from "../../columns.tsx";
import { loadFailure } from "../../errors.ts";
import { LoadMore } from "../../load-more.tsx";
import { PAGE_SIZE, usePaged } from "../../paged.ts";
import { placeholderScaling } from "../../placeholders.ts";
import { ALL_GROUPS, GroupFilter } from "../groups/group-filter.tsx";
import { useBackendGroupIds } from "../groups/group-ids.ts";
import { GroupLink } from "../groups/group-status.tsx";

const columns = columnsFor<ScalingLogView>();

/** The autoscaler's decisions, newest first, for every group or one. */
export function ScalingTab() {
  const { t } = useTranslation("web");
  const fetchScaling = useLogScalingHook();
  const groupIds = useBackendGroupIds();
  const [group, setGroup] = useState(ALL_GROUPS);
  const params = group === ALL_GROUPS ? {} : { groupId: group };
  const scaling = usePaged(
    getLogScalingQueryKey(params),
    async (cursor) =>
      (
        await fetchScaling({
          ...params,
          limit: PAGE_SIZE,
          ...(cursor === undefined ? {} : { cursor }),
        })
      ).data,
  );

  const scalingColumns = useMemo(
    () =>
      columns.columns([
        agoColumn(
          columns,
          "occurredAt",
          t("activity.audit.when"),
          (entry) => entry.occurredAt,
        ),
        columns.accessor(
          (entry) =>
            t(`overview.scaling.actions.${entry.action}`, {
              group: entry.groupId,
            }),
          {
            id: "decision",
            header: () => t("activity.scaling.decision"),
          },
        ),
        columns.accessor("reason", {
          header: () => t("activity.scaling.reason"),
          cell: (cell) => (
            <span className="text-text-2">
              {t(`overview.scaling.reasons.${cell.getValue()}`)}
            </span>
          ),
        }),
        columns.accessor("groupId", {
          header: () => t("groups.columns.group"),
          cell: (cell) => <GroupLink id={cell.getValue()} />,
        }),
      ]),
    [t],
  );

  return (
    <div className="flex flex-col gap-4">
      <GroupFilter groupIds={groupIds} value={group} onValueChange={setGroup} />
      <Card flush>
        <DataTable
          label={t("activity.tabs.scaling")}
          data={scaling.items}
          failure={loadFailure(scaling)}
          columns={scalingColumns}
          getRowId={(entry) => entry.id}
          placeholder={placeholderScaling}
          loading={scaling.pending}
          maxHeight="calc(100vh - 16rem)"
          empty={
            <EmptyState
              icon={TrendingUp}
              title={t("overview.scaling.empty")}
              description={t("activity.scaling.emptyDescription")}
            />
          }
        />
        <LoadMore paged={scaling} />
      </Card>
    </div>
  );
}
