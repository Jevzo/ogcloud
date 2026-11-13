import type { ServerView } from "@ogcloud/api-client";
import { Button, Drawer } from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import { ArrowRightLeft, ArrowUpRight, Square } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { DetailRow, Details } from "../../details.tsx";
import { Load } from "../../load.tsx";
import { GroupLink } from "../groups/group-status.tsx";
import type { useServerActions } from "./server-actions.tsx";
import { ServerStateBadge } from "./server-state.tsx";

/** A server's key facts beside the list, with its everyday actions and the way to its page (D-0231). */
export function ServerDrawer({
  server,
  onClose,
  actions,
}: {
  server: ServerView | null;
  onClose: () => void;
  actions: ReturnType<typeof useServerActions>;
}) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  if (server === null) return null;
  // The drawer closes first, so the action's dialog is the only layer on top.
  const run = (action: "move" | "stop") => {
    onClose();
    actions.open(action, server);
  };
  return (
    <Drawer
      open
      onOpenChange={(open) => (open ? undefined : onClose())}
      title={<span className="font-mono">{server.displayName}</span>}
      badges={<ServerStateBadge state={server.state} />}
      footer={
        <>
          {actions.can.command ? (
            <>
              <Button icon={Square} onClick={() => run("stop")}>
                {t("servers.actions.stop")}
              </Button>
              <Button icon={ArrowRightLeft} onClick={() => run("move")}>
                {t("servers.actions.move")}
              </Button>
            </>
          ) : null}
          <Button
            kind="primary"
            icon={ArrowUpRight}
            onClick={() =>
              void navigate({
                to: "/servers/$serverId",
                params: { serverId: server.id },
              })
            }
          >
            {t("actions.openPage")}
          </Button>
        </>
      }
    >
      <Details>
        <DetailRow label={t("servers.columns.players")}>
          <Load value={server.players} max={server.maxPlayers} />
        </DetailRow>
        <DetailRow label={t("servers.detail.since")}>
          <Ago at={server.stateSince} className="" />
        </DetailRow>
        {server.gameState == null ? null : (
          <DetailRow label={t("servers.columns.game")}>
            {t(`servers.game.${server.gameState}`)}
          </DetailRow>
        )}
        <DetailRow label={t("servers.columns.group")}>
          <GroupLink id={server.groupId} />
        </DetailRow>
        <DetailRow label={t("servers.detail.profile")}>
          <span className="font-mono text-mono">{server.profileId}</span>
        </DetailRow>
        <DetailRow label={t("servers.columns.heartbeat")}>
          <Ago at={server.lastHeartbeatAt} className="" />
        </DetailRow>
      </Details>
    </Drawer>
  );
}
