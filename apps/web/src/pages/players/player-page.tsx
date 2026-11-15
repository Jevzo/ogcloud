import type { AuditEntryView, PlayerView } from "@ogcloud/api-client";
import {
  useLogAudit,
  usePlayerGet,
  useServerList,
  useUserList,
} from "@ogcloud/api-client/react";
import {
  Badge,
  Button,
  Card,
  cn,
  FactTile,
  Loading,
  PageHeader,
  Value,
} from "@ogcloud/ui";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import {
  ArrowRightLeft,
  CircleCheck,
  CircleDashed,
  Crown,
  Download,
  History,
  LogOut,
  type LucideIcon,
  UserX,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { PlayerBody } from "../../avatar.tsx";
import { DetailFallback } from "../../detail-fallback.tsx";
import { DetailRow } from "../../details.tsx";
import { editorColumns } from "../../editing.tsx";
import { EventItem, EventsCard } from "../../event-item.tsx";
import { useFormat } from "../../format.ts";
import { IdentityCard } from "../../identity-card.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { useCan } from "../../permissions.ts";
import { placeholderPlayer } from "../../placeholders.ts";
import { RankLook } from "../permissions/rank-look.tsx";
import { usePrivacyActions } from "../privacy/privacy-actions.tsx";
import { usePlayerActions } from "./player-actions.tsx";
import { RankCell, useRankDisplay } from "./rank-cell.tsx";

const REFRESH_MS = 10_000;
// About as tall as the header with its tiles beside it.
const BODY_WIDTH = 96;
const RECENT_CHANGES = 6;
// Enough for a network's dashboard users; the users page pages through everyone.
const USERS_LIMIT = 200;

/** A player as the game shows them, where they are, how they look in game, and what was changed about them (D-0238). */
export function PlayerPage() {
  const { t } = useTranslation("web");
  const { playerId } = useParams({ from: "/app/players/$playerId" });
  const player = usePlayerGet(playerId, {
    query: { refetchInterval: REFRESH_MS },
  });
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
  const online = view.session != null;
  const ref = {
    uuid: view.uuid,
    name: view.name,
    permissionGroupId: view.permissionGroupId,
    online,
  };

  return (
    <Loading when={loading}>
      <div className="flex flex-col gap-6">
        <div className="grid grid-cols-[auto_minmax(0,1fr)] items-stretch gap-6">
          <figure className="m-0 grid place-items-center rounded-card border border-border-subtle bg-surface px-6 py-4 shadow-card">
            {loading ? (
              <span data-skeleton="" className="block h-58 w-24 rounded-card" />
            ) : (
              <PlayerBody uuid={view.uuid} width={BODY_WIDTH} />
            )}
          </figure>
          <div className="flex min-w-0 flex-col gap-4">
            <PageHeader
              title={<Value>{view.name}</Value>}
              badges={
                online ? (
                  <Badge tone="success" icon={CircleCheck}>
                    {t("players.detail.onlineNow")}
                  </Badge>
                ) : (
                  <Badge tone="neutral" icon={CircleDashed}>
                    {t("players.detail.offline")}
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
                      {online ? (
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
                      ) : null}
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
            <PlayerFacts view={view} />
          </div>
        </div>
        <div className={editorColumns}>
          <InGameCard view={view} />
          <div className="flex min-w-0 flex-col gap-6">
            <RecentChanges uuid={view.uuid} enabled={!loading} />
            <PlayerIdentity view={view} />
          </div>
        </div>
        {actions.dialogs}
        {privacy.dialogs}
      </div>
    </Loading>
  );
}

/** How long they are on and where, or when they were last seen; and since when the network knows them. */
function PlayerFacts({ view }: { view: PlayerView }) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const servers = useServerList(undefined, {
    query: { refetchInterval: REFRESH_MS },
  });
  const now = Date.now();
  const session = view.session ?? null;
  const all = servers.data?.data ?? [];
  const server = all.find((candidate) => candidate.id === session?.serverId);
  const proxy = all.find((candidate) => candidate.id === session?.proxyId);
  const at = (iso: string) => format.dateTime(new Date(iso));

  return (
    // Offline shows two tiles, which share the row rather than leave a third of it empty.
    <div
      className={cn(
        "grid gap-6",
        session === null ? "grid-cols-2" : "grid-cols-3",
      )}
    >
      {session === null ? (
        <FactTile
          label={t("players.columns.lastSeen")}
          value={format.ago(new Date(view.lastSeenAt), now)}
          caption={at(view.lastSeenAt)}
        />
      ) : (
        <>
          <FactTile
            label={t("players.detail.connectedFor")}
            value={format.since(new Date(session.connectedAt), now)}
            caption={t("players.detail.since", {
              at: at(session.connectedAt),
            })}
          />
          <FactTile
            label={t("players.detail.onServer")}
            value={
              server === undefined ? (
                "—"
              ) : (
                <Link
                  to="/servers/$serverId"
                  params={{ serverId: server.id }}
                  className="font-mono hover:underline"
                >
                  {server.displayName}
                </Link>
              )
            }
            caption={
              server === undefined
                ? proxy === undefined
                  ? undefined
                  : t("players.detail.via", { proxy: proxy.displayName })
                : t("players.detail.serverCaption", {
                    group: server.groupId,
                    players: format.number(server.players),
                    max: format.number(server.maxPlayers),
                  })
            }
          />
        </>
      )}
      <FactTile
        label={t("players.detail.knownFor")}
        value={format.since(new Date(view.firstSeenAt), now)}
        caption={t("players.detail.firstSeen", { at: at(view.firstSeenAt) })}
      />
    </div>
  );
}

/** Their rank's look with their own name, as chat, the tablist and their nametag show it (D-0238). */
function InGameCard({ view }: { view: PlayerView }) {
  const { t } = useTranslation("web");
  const display = useRankDisplay(view.permissionGroupId);
  return display === undefined ? null : (
    <Card title={t("players.detail.inGame")}>
      <RankLook display={display} name={view.name} />
    </Card>
  );
}

const CHANGES: readonly {
  match: RegExp;
  key: "rank" | "transfer" | "export";
  icon: LucideIcon;
  tint: string;
}[] = [
  {
    match: /\/rank$/,
    key: "rank",
    icon: Crown,
    tint: "bg-accent-soft text-accent",
  },
  {
    match: /\/transfer$/,
    key: "transfer",
    icon: ArrowRightLeft,
    tint: "bg-info-soft text-info",
  },
  {
    match: /^GET \/api\/v1\/privacy\//,
    key: "export",
    icon: Download,
    tint: "bg-surface-3 text-text-2",
  },
];

/** What was done to the player through the API or in game, newest first, from the audit log (D-0213). */
function RecentChanges({ uuid, enabled }: { uuid: string; enabled: boolean }) {
  const { t } = useTranslation("web");
  const canRead = useCan("logs:read");
  const audit = useLogAudit(
    { targetId: uuid, limit: RECENT_CHANGES },
    { query: { enabled: enabled && canRead } },
  );
  if (!canRead) return null;
  const entries = audit.data?.data.items ?? [];
  return (
    <EventsCard
      title={t("players.detail.changes.title")}
      empty={t("players.detail.changes.empty")}
      loading={audit.isPending}
    >
      {entries.map((entry) => (
        <ChangeItem key={entry.id} entry={entry} />
      ))}
    </EventsCard>
  );
}

function ChangeItem({ entry }: { entry: AuditEntryView }) {
  const { t } = useTranslation("web");
  const change = CHANGES.find(({ match }) => match.test(entry.action));
  return (
    <EventItem
      icon={change?.icon ?? History}
      tint={change?.tint ?? "bg-surface-3 text-text-2"}
      title={
        change === undefined
          ? entry.action
          : t(`players.detail.changes.${change.key}`)
      }
      detail={t("players.detail.changes.by", { actor: entry.actor })}
      at={entry.occurredAt}
    />
  );
}

/** The UUID to copy, and the dashboard user who linked this player, for those who may see users (D-0106). */
function PlayerIdentity({ view }: { view: PlayerView }) {
  const { t } = useTranslation("web");
  const canReadUsers = useCan("users:read");
  const users = useUserList(
    { limit: USERS_LIMIT },
    { query: { enabled: canReadUsers } },
  );
  const linked = users.data?.data.items.find(
    (user) => user.linkedPlayerUuid === view.uuid,
  );
  return (
    <IdentityCard label={t("players.detail.uuid")} id={view.uuid}>
      {linked === undefined ? undefined : (
        <DetailRow label={t("players.detail.dashboardUser")}>
          <Link
            to="/users/$userId"
            params={{ userId: linked.id }}
            className="font-label hover:underline"
          >
            {linked.username}
          </Link>
        </DetailRow>
      )}
    </IdentityCard>
  );
}
