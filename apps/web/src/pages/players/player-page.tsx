import { usePlayerGet } from "@ogcloud/api-client/react";
import { Badge, Button, Card, Loading, PageHeader, Value } from "@ogcloud/ui";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import {
  ArrowRightLeft,
  CircleCheck,
  CircleDashed,
  Crown,
  LogOut,
  UserX,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { DetailFallback } from "../../detail-fallback.tsx";
import { DetailRow, Details } from "../../details.tsx";
import { IdentityCard } from "../../identity-card.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { placeholderPlayer } from "../../placeholders.ts";
import { usePrivacyActions } from "../privacy/privacy-actions.tsx";
import { useServerNames } from "../servers/server-names.ts";
import { usePlayerActions } from "./player-actions.tsx";
import { RankCell } from "./rank-cell.tsx";

const REFRESH_MS = 10_000;

/** One player: where they are, their rank and since when the network knows them. */
export function PlayerPage() {
  const { t } = useTranslation("web");
  const { playerId } = useParams({ from: "/app/players/$playerId" });
  const player = usePlayerGet(playerId, {
    query: { refetchInterval: REFRESH_MS },
  });
  const servers = useServerNames();
  const actions = usePlayerActions();
  const navigate = useNavigate();
  // An erased player is gone, so the page goes back to the list.
  const privacy = usePrivacyActions(() => void navigate({ to: "/players" }));

  if (player.data === undefined && player.error !== null) {
    return (
      <DetailFallback
        error={player.error}
        icon={UserX}
        goneTitle={t("players.detail.unknown")}
        goneDescription={t("players.detail.unknownDescription")}
        failedTitle={t("players.detail.failed")}
        back={
          <Link to="/players" className="font-label text-accent">
            {t("players.detail.back")}
          </Link>
        }
      />
    );
  }
  const loading = player.data === undefined;
  const view = player.data?.data ?? placeholderPlayer;
  const session = view.session ?? null;
  const ref = {
    uuid: view.uuid,
    name: view.name,
    permissionGroupId: view.permissionGroupId,
    online: session !== null,
  };
  const where = (id: string | null | undefined) =>
    id == null ? "—" : (servers.get(id) ?? id);

  return (
    <Loading when={loading}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title={<Value>{view.name}</Value>}
          badges={
            session === null ? (
              <Badge tone="neutral" icon={CircleDashed}>
                {t("players.detail.offline")}
              </Badge>
            ) : (
              <Badge tone="success" icon={CircleCheck}>
                {t("players.detail.onlineNow")}
              </Badge>
            )
          }
          actions={
            <>
              <MoreMenu
                groups={[
                  privacy.itemsFor({
                    kind: "player",
                    id: view.uuid,
                    name: view.name,
                  }),
                ]}
              />
              {actions.canWrite ? (
                <>
                  {session === null ? null : (
                    <>
                      <Button
                        icon={LogOut}
                        onClick={() => actions.open("kick", ref)}
                      >
                        {t("players.actions.kick")}
                      </Button>
                      <Button
                        icon={ArrowRightLeft}
                        onClick={() => actions.open("transfer", ref)}
                      >
                        {t("players.actions.transfer")}
                      </Button>
                    </>
                  )}
                  <Button
                    kind="primary"
                    icon={Crown}
                    onClick={() => actions.open("rank", ref)}
                  >
                    {t("players.actions.rank")}
                  </Button>
                </>
              ) : null}
            </>
          }
        />
        <div className="grid grid-cols-[minmax(0,1fr)_minmax(var(--container-side-min),0.6fr)] items-start gap-6">
          <Card title={t("players.detail.status")}>
            <Details>
              {session === null ? (
                <DetailRow label={t("players.columns.lastSeen")}>
                  <Ago at={view.lastSeenAt} className="" />
                </DetailRow>
              ) : (
                <>
                  <DetailRow label={t("players.columns.server")}>
                    <span className="font-mono text-mono">
                      {where(session.serverId)}
                    </span>
                  </DetailRow>
                  <DetailRow label={t("players.columns.proxy")}>
                    <span className="font-mono text-mono">
                      {where(session.proxyId)}
                    </span>
                  </DetailRow>
                  <DetailRow label={t("players.columns.connected")}>
                    <Ago at={session.connectedAt} className="" />
                  </DetailRow>
                </>
              )}
              <DetailRow label={t("players.columns.rank")}>
                <span className="inline-flex items-center gap-2">
                  <RankCell
                    groupId={view.permissionGroupId}
                    expiresAt={view.rankExpiresAt}
                  />
                  {view.rankExpiresAt == null ? (
                    <span className="text-meta text-text-3">
                      {t("players.detail.permanent")}
                    </span>
                  ) : null}
                </span>
              </DetailRow>
              <DetailRow label={t("players.columns.firstSeen")}>
                <Ago at={view.firstSeenAt} className="" />
              </DetailRow>
            </Details>
          </Card>
          <IdentityCard label={t("players.detail.uuid")} id={view.uuid} />
        </div>
        {actions.dialogs}
        {privacy.dialogs}
      </div>
    </Loading>
  );
}
