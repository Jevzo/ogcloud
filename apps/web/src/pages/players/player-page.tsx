import { usePlayerGet } from "@ogcloud/api-client/react";
import { Badge, Button, Card, CopyField, PageHeader } from "@ogcloud/ui";
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
import { DetailFallback } from "../../detail-fallback.tsx";
import { DetailRow, Details } from "../../details.tsx";
import { useFormat } from "../../format.ts";
import { MoreMenu } from "../../more-menu.tsx";
import { usePrivacyActions } from "../privacy/privacy-actions.tsx";
import { useServerNames } from "../servers/server-names.ts";
import { usePlayerActions } from "./player-actions.tsx";

const REFRESH_MS = 10_000;

/** One player: where they are, their rank and since when the network knows them. */
export function PlayerPage() {
  const { t } = useTranslation("web");
  const format = useFormat();
  const { playerId } = useParams({ from: "/app/players/$playerId" });
  const player = usePlayerGet(playerId, {
    query: { refetchInterval: REFRESH_MS },
  });
  const servers = useServerNames();
  const actions = usePlayerActions();
  const navigate = useNavigate();
  // An erased player is gone, so the page goes back to the list.
  const privacy = usePrivacyActions(() => void navigate({ to: "/players" }));

  if (player.data === undefined) {
    return (
      <DetailFallback
        error={player.error}
        icon={UserX}
        goneTitle={t("players.detail.unknown")}
        goneDescription={t("players.detail.unknownDescription")}
        failedTitle={t("players.detail.failed")}
        back={
          <Link to="/players" className="font-label text-accent-text">
            {t("players.detail.back")}
          </Link>
        }
      />
    );
  }
  const view = player.data.data;
  const session = view.session ?? null;
  const ref = {
    uuid: view.uuid,
    name: view.name,
    permissionGroupId: view.permissionGroupId,
    online: session !== null,
  };
  const now = Date.now();
  const where = (id: string | null | undefined) =>
    id == null ? "—" : (servers.get(id) ?? id);

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {view.name}
            {session === null ? (
              <Badge tone="neutral" icon={CircleDashed}>
                {t("players.detail.offline")}
              </Badge>
            ) : (
              <Badge tone="success" icon={CircleCheck}>
                {t("players.detail.onlineNow")}
              </Badge>
            )}
          </span>
        }
        description={t("players.detail.description", {
          ago: format.ago(new Date(view.firstSeenAt), now),
        })}
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
      <div className="grid grid-cols-[minmax(0,1fr)_minmax(var(--container-side-min),0.6fr)] items-start gap-4">
        <Card title={t("players.detail.status")}>
          <Details>
            {session === null ? (
              <DetailRow label={t("players.columns.lastSeen")}>
                {format.ago(new Date(view.lastSeenAt), now)}
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
                  {format.ago(new Date(session.connectedAt), now)}
                </DetailRow>
              </>
            )}
            <DetailRow label={t("players.columns.rank")}>
              {view.rankExpiresAt == null
                ? t("players.detail.permanent", {
                    group: view.permissionGroupId,
                  })
                : t("players.rankUntil", {
                    group: view.permissionGroupId,
                    ago: format.ago(new Date(view.rankExpiresAt), now),
                  })}
            </DetailRow>
            <DetailRow label={t("players.columns.firstSeen")}>
              {format.ago(new Date(view.firstSeenAt), now)}
            </DetailRow>
          </Details>
        </Card>
        <Card title={t("players.detail.identity")}>
          <div className="flex flex-col gap-1.5">
            <span className="font-label">{t("players.detail.uuid")}</span>
            <CopyField value={view.uuid} label={t("players.detail.uuid")} />
          </div>
        </Card>
      </div>
      {actions.dialogs}
      {privacy.dialogs}
    </div>
  );
}
