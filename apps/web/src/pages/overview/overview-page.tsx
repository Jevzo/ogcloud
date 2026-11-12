import { useOverviewGet } from "@ogcloud/api-client/react";
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
import { PlayersCard } from "./players-card.tsx";
import { ScalingCard } from "./scaling-card.tsx";
import { ServersCard } from "./servers-card.tsx";

const GRID =
  "grid grid-cols-[minmax(0,2fr)_minmax(var(--container-side-min),1fr)] gap-4";

/** The network at a glance (D-0104): players, servers, groups and the autoscaler's latest decisions. */
export function OverviewPage() {
  const { t } = useTranslation("web");
  const overview = useOverviewGet();
  const groupActions = useGroupActions();

  if (overview.data === undefined && overview.isError) {
    return (
      <div className="flex flex-col gap-4">
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
      <div className="flex flex-col gap-4">
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
        <div className={GRID}>
          <PlayersCard online={data.playersOnline} max={data.maxPlayers} />
          <ServersCard
            byState={data.serversByState}
            paused={data.groups.filter((group) => group.crashLoopPaused)}
            onResume={(id) => groupActions.request("resume", id)}
          />
          <GroupsCard
            groups={data.groups}
            actionsFor={groupActions.actionsFor}
          />
          <ScalingCard entries={data.recentScaling} />
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
