import { useOverviewGet, useServerGet } from "@ogcloud/api-client/react";
import { Button, Card, KpiTile, Loading, PageHeader, Value } from "@ogcloud/ui";
import { Link, useParams } from "@tanstack/react-router";
import {
  ArrowRightLeft,
  OctagonX,
  ServerOff,
  Square,
  SquareTerminal,
  Upload,
} from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { DetailFallback } from "../../detail-fallback.tsx";
import { DetailRow, Details } from "../../details.tsx";
import { useFormat } from "../../format.ts";
import { IdentityCard } from "../../identity-card.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { placeholderServer } from "../../placeholders.ts";
import { GroupLink } from "../groups/group-status.tsx";
import { usePlayerActions } from "../players/player-actions.tsx";
import { OnlineTable } from "../players/players-page.tsx";
import { useServerActions } from "./server-actions.tsx";
import { ServerStateBadge } from "./server-state.tsx";

const REFRESH_MS = 10_000;

// A server this full warns, like its capacity meters.
const FULL_AT = 0.9;

/** One server: its players, where it is in its life, and what can be done to it. */
export function ServerPage() {
  const { t } = useTranslation("web");
  const { serverId } = useParams({ from: "/app/servers/$serverId" });
  const server = useServerGet(serverId, {
    query: { refetchInterval: REFRESH_MS },
  });
  const overview = useOverviewGet();
  const actions = useServerActions(
    overview.data?.data.groups.map((group) => group.id) ?? [],
  );
  const playerActions = usePlayerActions();
  const format = useFormat();
  const proxy = server.data?.data.groupType === "PROXY";
  const here = useMemo(() => ({ id: serverId, proxy }), [serverId, proxy]);

  if (server.data === undefined && server.error !== null) {
    return (
      <DetailFallback
        error={server.error}
        icon={ServerOff}
        goneTitle={t("servers.detail.gone")}
        goneDescription={t("servers.detail.goneDescription")}
        failedTitle={t("servers.detail.failed")}
        back={
          <Link to="/servers" className="font-label text-accent">
            {t("servers.detail.back")}
          </Link>
        }
      />
    );
  }
  const loading = server.data === undefined;
  const view = server.data?.data ?? placeholderServer;
  const ref = {
    id: view.id,
    displayName: view.displayName,
    groupId: view.groupId,
    groupType: view.groupType,
  };
  const more = [
    ...(actions.can.push
      ? [
          [
            {
              label: t("servers.actions.push"),
              icon: Upload,
              onSelect: () => actions.open("push", ref),
            },
          ],
        ]
      : []),
    ...(actions.can.command
      ? [
          [
            {
              label: t("servers.actions.kill"),
              icon: OctagonX,
              danger: true,
              onSelect: () => actions.open("kill", ref),
            },
          ],
        ]
      : []),
  ];

  return (
    <Loading when={loading}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title={<Value className="font-mono">{view.displayName}</Value>}
          badges={<ServerStateBadge state={view.state} />}
          actions={
            <>
              <MoreMenu groups={more} />
              {actions.can.command ? (
                <>
                  <Button
                    icon={SquareTerminal}
                    onClick={() => actions.open("command", ref)}
                  >
                    {t("servers.actions.command")}
                  </Button>
                  <Button
                    icon={ArrowRightLeft}
                    onClick={() => actions.open("move", ref)}
                  >
                    {t("servers.actions.move")}
                  </Button>
                  <Button
                    icon={Square}
                    onClick={() => actions.open("stop", ref)}
                  >
                    {t("servers.actions.stop")}
                  </Button>
                </>
              ) : null}
            </>
          }
        />
        <div className="grid grid-cols-[minmax(0,1fr)_minmax(var(--container-side-min),0.6fr)] items-start gap-6">
          <div className="flex min-w-0 flex-col gap-6">
            <KpiTile
              label={t("servers.columns.players")}
              value={view.players}
              format={format.number}
              caption={t("groups.limit", {
                max: format.number(view.maxPlayers),
              })}
              warn={
                view.maxPlayers > 0 && view.players / view.maxPlayers >= FULL_AT
              }
            />
            {loading ? null : (
              <Card
                flush
                title={t(
                  proxy ? "servers.detail.throughHere" : "servers.detail.here",
                )}
              >
                <OnlineTable actions={playerActions} server={here} />
              </Card>
            )}
          </div>
          <div className="flex min-w-0 flex-col gap-6">
            {/* The title's badge names the state; this card says since when and the rest. */}
            <Card title={t("servers.detail.status")}>
              <Details>
                <DetailRow label={t("servers.detail.since")}>
                  <Ago at={view.stateSince} className="" />
                </DetailRow>
                {view.gameState == null ? null : (
                  <DetailRow label={t("servers.columns.game")}>
                    {t(`servers.game.${view.gameState}`)}
                  </DetailRow>
                )}
                <DetailRow label={t("servers.columns.started")}>
                  <Ago at={view.startedAt} className="" />
                </DetailRow>
                <DetailRow label={t("servers.columns.heartbeat")}>
                  <Ago at={view.lastHeartbeatAt} className="" />
                </DetailRow>
                <DetailRow label={t("servers.columns.group")}>
                  <GroupLink id={view.groupId} />
                </DetailRow>
                <DetailRow label={t("servers.detail.profile")}>
                  <span className="font-mono text-mono">{view.profileId}</span>
                </DetailRow>
              </Details>
            </Card>
            <IdentityCard
              label={t("servers.detail.id")}
              id={view.id}
              hint={t("servers.detail.idHint")}
            />
          </div>
        </div>
        {actions.dialogs}
        {playerActions.dialogs}
      </div>
    </Loading>
  );
}
