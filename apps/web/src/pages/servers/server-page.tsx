import { ApiError } from "@ogcloud/api-client";
import { useOverviewGet, useServerGet } from "@ogcloud/api-client/react";
import {
  Button,
  Card,
  CopyField,
  EmptyState,
  Menu,
  Meter,
  PageHeader,
  Skeleton,
} from "@ogcloud/ui";
import { Link, useParams } from "@tanstack/react-router";
import {
  ArrowRightLeft,
  Ellipsis,
  OctagonX,
  ServerOff,
  Square,
  SquareTerminal,
  Upload,
} from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useFormat } from "../../format.ts";
import { useServerActions } from "./server-actions.tsx";
import { ServerStateBadge } from "./server-state.tsx";

const REFRESH_MS = 10_000;
const NOT_FOUND = 404;

/** One server: where it is in its life, its players, and what can be done to it. */
export function ServerPage() {
  const { t } = useTranslation("web");
  const format = useFormat();
  const { serverId } = useParams({ from: "/app/servers/$serverId" });
  const server = useServerGet(serverId, {
    query: { refetchInterval: REFRESH_MS },
  });
  const overview = useOverviewGet();
  const actions = useServerActions(
    overview.data?.data.groups.map((group) => group.id) ?? [],
  );

  if (server.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-64 rounded-card" />
      </div>
    );
  }
  if (server.isError) {
    const gone =
      server.error instanceof ApiError && server.error.status === NOT_FOUND;
    return (
      <EmptyState
        icon={ServerOff}
        title={gone ? t("servers.detail.gone") : t("servers.detail.failed")}
        description={
          gone
            ? t("servers.detail.goneDescription")
            : server.error instanceof Error
              ? server.error.message
              : ""
        }
        action={
          <Link to="/servers" className="font-label text-accent-text">
            {t("servers.detail.back")}
          </Link>
        }
      />
    );
  }
  const view = server.data.data;
  const ref = {
    id: view.id,
    displayName: view.displayName,
    groupId: view.groupId,
    groupType: view.groupType,
  };
  const now = Date.now();
  const ago = (at: string | null | undefined) =>
    at == null ? "—" : format.ago(new Date(at), now);
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
    <div className="flex flex-col gap-4">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <span className="font-mono">{view.displayName}</span>
            <ServerStateBadge state={view.state} />
          </span>
        }
        description={t("servers.detail.description", {
          type: t(`groups.type.${view.groupType}`),
          group: view.groupId,
          profile: view.profileId,
        })}
        actions={
          <>
            {more.length > 0 ? (
              <Menu
                groups={more}
                trigger={
                  <Button icon={Ellipsis} iconOnly>
                    {t("servers.detail.more")}
                  </Button>
                }
              />
            ) : null}
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
                <Button icon={Square} onClick={() => actions.open("stop", ref)}>
                  {t("servers.actions.stop")}
                </Button>
              </>
            ) : null}
          </>
        }
      />
      <div className="grid grid-cols-[minmax(0,1fr)_minmax(320px,0.6fr)] gap-4">
        <Card title={t("servers.detail.status")}>
          <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-8 gap-y-3">
            <Row label={t("servers.columns.state")}>
              <span className="flex items-center gap-2">
                <ServerStateBadge state={view.state} />
                <span className="text-text-3">{ago(view.stateSince)}</span>
              </span>
            </Row>
            <Row label={t("servers.columns.players")}>
              <span className="inline-flex items-center gap-2.5">
                <Meter value={view.players} max={view.maxPlayers} />
                {t("groups.load", {
                  players: format.number(view.players),
                  capacity: format.number(view.maxPlayers),
                })}
              </span>
            </Row>
            {view.gameState == null ? null : (
              <Row label={t("servers.columns.game")}>
                {t(`servers.game.${view.gameState}`)}
              </Row>
            )}
            <Row label={t("servers.columns.started")}>
              {ago(view.startedAt)}
            </Row>
            <Row label={t("servers.columns.heartbeat")}>
              {ago(view.lastHeartbeatAt)}
            </Row>
            <Row label={t("servers.columns.group")}>
              <Link to="/groups" className="text-accent-text hover:underline">
                {view.groupId}
              </Link>
            </Row>
            <Row label={t("servers.detail.profile")}>
              <span className="font-mono text-mono">{view.profileId}</span>
            </Row>
          </dl>
        </Card>
        <Card title={t("servers.detail.identity")}>
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <span className="font-label">{t("servers.detail.id")}</span>
              <CopyField value={view.id} label={t("servers.detail.id")} />
            </div>
            <p className="text-small text-text-3">
              {t("servers.detail.idHint")}
            </p>
          </div>
        </Card>
      </div>
      {actions.dialogs}
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-text-3">{label}</dt>
      <dd className="m-0">{children}</dd>
    </>
  );
}
