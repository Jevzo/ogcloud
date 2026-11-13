import { useOverviewGet, useOverviewHistory } from "@ogcloud/api-client/react";
import {
  Button,
  Card,
  ConfirmDialog,
  Loading,
  Note,
  PageHeader,
  useToast,
} from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import { Construction, FileWarning, Play, Plus } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useConfirmFailure, useFailureToast } from "../../errors.ts";
import { useCan } from "../../permissions.ts";
import { placeholderOverview } from "../../placeholders.ts";
import { useGroupActions } from "../groups/group-actions.tsx";
import { MaintenanceBadge } from "../network/maintenance-badge.tsx";
import { useNetworkMaintenance } from "../network/network-actions.ts";
import { RequestServerDialog } from "../servers/request-server-dialog.tsx";
import { GroupsCard } from "./groups-card.tsx";
import { KpiRow } from "./kpi-row.tsx";
import { PausedCard } from "./paused-card.tsx";
import { PlayersCard } from "./players-card.tsx";
import { ScalingCard } from "./scaling-card.tsx";

// The controller samples once a minute (D-0213).
const HISTORY_REFRESH_MS = 60_000;
// Thirds, like the KPI row above, so their edges line up. Each column stacks its own cards, so a card keeps its
// content's height instead of its neighbour's (D-0231).
const GRID = "grid grid-cols-3 items-start gap-6";
const COLUMN = "flex min-w-0 flex-col gap-6";

/** The network at a glance (D-0104): players, servers, groups and the autoscaler's latest decisions. */
export function OverviewPage() {
  const { t } = useTranslation("web");
  const overview = useOverviewGet();
  const history = useOverviewHistory(undefined, {
    query: { refetchInterval: HISTORY_REFRESH_MS },
  });
  const groupActions = useGroupActions();

  if (overview.data === undefined && overview.isError) {
    return (
      <div className="flex flex-col gap-6">
        <PageHeader title={t("nav.overview")} />
        <Card>
          {t("errors.generic", {
            detail:
              overview.error instanceof Error ? overview.error.message : "",
          })}
        </Card>
      </div>
    );
  }
  const loading = overview.data === undefined;
  const data = overview.data?.data ?? placeholderOverview;

  return (
    <Loading when={loading}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title={t("nav.overview")}
          badges={<MaintenanceBadge maintenance={data.maintenance} />}
          actions={
            <OverviewActions
              maintenance={data.maintenance}
              groupIds={data.groups.map((group) => group.id)}
            />
          }
        />
        {data.eulaAccepted ? null : (
          <Note
            tone="warning"
            icon={FileWarning}
            action={
              <Link
                to="/network"
                className="font-label text-small underline-offset-2 hover:underline"
              >
                {t("overview.eulaAction")}
              </Link>
            }
          >
            {t("overview.eula")}
          </Note>
        )}
        <KpiRow data={data} history={history.data?.data.points ?? []} />
        <div className={GRID}>
          <div className={`${COLUMN} col-span-2`}>
            <PlayersCard history={history} />
            <GroupsCard
              groups={data.groups}
              actionsFor={groupActions.actionsFor}
            />
          </div>
          <div className={COLUMN}>
            <PausedCard
              paused={data.groups.filter((group) => group.crashLoopPaused)}
              onResume={(id) => groupActions.request("resume", id)}
            />
            <ScalingCard entries={data.recentScaling} />
          </div>
        </div>
        {groupActions.dialogs}
      </div>
    </Loading>
  );
}

/** Network maintenance and a new server, for those allowed to do either (D-0210: primary last). */
function OverviewActions({
  maintenance,
  groupIds,
}: {
  maintenance: boolean;
  groupIds: readonly string[];
}) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const keepOpen = useConfirmFailure();
  const canMaintain = useCan("network:write");
  const canRequest = useCan("servers:command");
  const setMaintenance = useNetworkMaintenance();
  const [confirming, setConfirming] = useState(false);
  const [requesting, setRequesting] = useState(false);

  const endMaintenance = () =>
    setMaintenance.mutate(false, {
      onSuccess: () => toast({ message: t("overview.maintenanceEnded") }),
      onError: failed,
    });

  return (
    <>
      {canMaintain ? (
        maintenance ? (
          <Button
            icon={Play}
            data-skeleton=""
            onClick={endMaintenance}
            loading={setMaintenance.isPending}
          >
            {t("overview.endMaintenance")}
          </Button>
        ) : (
          <Button
            icon={Construction}
            data-skeleton=""
            onClick={() => setConfirming(true)}
          >
            {t("overview.startMaintenance")}
          </Button>
        )
      ) : null}
      {canRequest ? (
        <Button kind="primary" icon={Plus} onClick={() => setRequesting(true)}>
          {t("servers.request.submit")}
        </Button>
      ) : null}
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("overview.maintenanceTitle")}
        description={t("overview.maintenanceDescription")}
        confirmLabel={t("overview.startMaintenance")}
        onConfirm={() =>
          setMaintenance
            .mutateAsync(true)
            .then(
              () => toast({ message: t("overview.maintenanceStarted") }),
              keepOpen,
            )
        }
      />
      <RequestServerDialog
        open={requesting}
        onOpenChange={setRequesting}
        groupIds={groupIds}
      />
    </>
  );
}
