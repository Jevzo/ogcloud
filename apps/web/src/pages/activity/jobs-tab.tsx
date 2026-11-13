import type { JobView } from "@ogcloud/api-client";
import { getJobListQueryKey, useJobListHook } from "@ogcloud/api-client/react";
import {
  Badge,
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  type Tone,
} from "@ogcloud/ui";
import {
  CircleCheck,
  CircleDashed,
  CircleX,
  ListChecks,
  LoaderCircle,
  type LucideIcon,
} from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { agoColumn, monoColumn } from "../../columns.tsx";
import { loadFailure } from "../../errors.ts";
import { Load } from "../../load.tsx";
import { LoadMore } from "../../load-more.tsx";
import { PAGE_SIZE, usePaged } from "../../paged.ts";
import { placeholderJob } from "../../placeholders.ts";

// Progress also arrives over the event stream; polling covers events lost to at-most-once delivery (D-0105).
const RUNNING_REFRESH_MS = 3_000;
const columns = columnsFor<JobView>();

const STATE_LOOK: Record<JobView["state"], { tone: Tone; icon: LucideIcon }> = {
  QUEUED: { tone: "neutral", icon: CircleDashed },
  RUNNING: { tone: "info", icon: LoaderCircle },
  SUCCEEDED: { tone: "success", icon: CircleCheck },
  FAILED: { tone: "danger", icon: CircleX },
};

// The API names kinds as dotted strings (the domain's JobKinds); the catalog cannot use dots in keys.
const KINDS = {
  "group.restart": "groupRestart",
  "group.delete": "groupDelete",
  "network.restart": "networkRestart",
  "forwarding.rotate": "forwardingRotate",
  "bundle.rollout": "bundleRollout",
} as const;

function useKindLabel() {
  const { t } = useTranslation("web");
  return (job: JobView) => {
    const key = KINDS[job.kind as keyof typeof KINDS];
    const subject = job.payload.groupId ?? job.payload.profileId;
    const kind = key === undefined ? job.kind : t(`activity.jobs.kinds.${key}`);
    return subject === undefined
      ? kind
      : t("activity.jobs.kindFor", { kind, subject });
  };
}

/** Long-running operations the controller runs step by step (D-0039), with their progress. */
export function JobsTab() {
  const { t } = useTranslation("web");
  const kindOf = useKindLabel();
  const fetchJobs = useJobListHook();
  const jobs = usePaged(
    getJobListQueryKey(),
    async (cursor) =>
      (
        await fetchJobs({
          limit: PAGE_SIZE,
          ...(cursor === undefined ? {} : { cursor }),
        })
      ).data,
    RUNNING_REFRESH_MS,
  );

  const jobColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor(kindOf, {
          id: "kind",
          header: () => t("activity.jobs.kind"),
        }),
        columns.accessor("state", {
          header: () => t("groups.columns.state"),
          cell: (cell) => (
            <Badge {...STATE_LOOK[cell.getValue()]}>
              {t(`activity.jobs.state.${cell.getValue()}`)}
            </Badge>
          ),
        }),
        columns.accessor("completedSteps", {
          header: () => t("activity.jobs.progress"),
          meta: { numeric: true },
          cell: (cell) => (
            <Load
              kind="progress"
              value={cell.getValue()}
              max={cell.row.original.totalSteps}
            />
          ),
        }),
        columns.accessor("message", {
          header: () => t("activity.jobs.message"),
          cell: (cell) => (
            <span className="text-text-2">{cell.getValue()}</span>
          ),
        }),
        monoColumn(
          columns,
          "createdBy",
          t("templates.columns.by"),
          (job) => job.createdBy,
        ),
        agoColumn(
          columns,
          "createdAt",
          t("activity.audit.when"),
          (job) => job.createdAt,
        ),
      ]),
    [t, kindOf],
  );

  return (
    <Card flush>
      <DataTable
        label={t("activity.tabs.jobs")}
        data={jobs.items}
        failure={loadFailure(jobs)}
        columns={jobColumns}
        getRowId={(job) => job.id}
        placeholder={placeholderJob}
        loading={jobs.pending}
        maxHeight="calc(100vh - 14rem)"
        empty={
          <EmptyState
            icon={ListChecks}
            title={t("activity.jobs.empty")}
            description={t("activity.jobs.emptyDescription")}
          />
        }
      />
      <LoadMore paged={jobs} />
    </Card>
  );
}
