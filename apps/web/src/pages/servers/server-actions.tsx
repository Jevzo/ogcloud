import type { ServerViewGroupType } from "@ogcloud/api-client";
import {
  getOverviewGetQueryKey,
  getServerListQueryKey,
  useServerKill,
  useServerPushTemplate,
  useServerStop,
} from "@ogcloud/api-client/react";
import {
  ConfirmDialog,
  type MenuItem,
  type RowActions,
  useToast,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import {
  ArrowRightLeft,
  ArrowUpRight,
  type LucideIcon,
  OctagonX,
  Square,
  SquareTerminal,
  Upload,
} from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useConfirmFailure } from "../../errors.ts";
import { usePendingAction } from "../../pending-action.ts";
import { useCan } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";
import { CommandDialog } from "./command-dialog.tsx";
import { MovePlayersDialog } from "./move-players-dialog.tsx";

/** What the actions need to know about a server. */
export interface ServerRef {
  id: string;
  displayName: string;
  groupId: string;
  groupType: ServerViewGroupType;
}

export type ServerAction = "stop" | "kill" | "push" | "move" | "command";

/**
 * A server's actions with their dialogs (D-0210): stopping asks first, killing wants the server's name typed, pushing
 * its files makes a new template version. The servers list and a server's page share them.
 */
export function useServerActions(groupIds: readonly string[]): {
  actionsFor: (server: ServerRef) => RowActions;
  open: (action: ServerAction, server: ServerRef) => void;
  can: { command: boolean; push: boolean };
  dialogs: ReactNode;
} {
  const { t } = useTranslation("web");
  const toast = useToast();
  const keepOpen = useConfirmFailure();
  const navigate = useNavigate();
  const client = useQueryClient();
  const can = {
    command: useCan("servers:command"),
    push: useCan("templates:write"),
  };
  const pending = usePendingAction<ServerAction, ServerRef>();
  const refresh = () =>
    invalidatePaths(client, [
      getServerListQueryKey()[0],
      getOverviewGetQueryKey()[0],
    ]);
  const stop = useServerStop({ mutation: { onSettled: refresh } });
  const kill = useServerKill({ mutation: { onSettled: refresh } });
  const push = useServerPushTemplate();

  const { open, onOpenChange: close, isOpen } = pending;
  const item = (
    action: ServerAction,
    server: ServerRef,
    icon: LucideIcon,
    danger = false,
  ): MenuItem => ({
    label: t(`servers.actions.${action}`),
    icon,
    danger,
    onSelect: () => open(action, server),
  });

  const actionsFor = (server: ServerRef): RowActions => ({
    ...(can.command
      ? {
          inline: [
            {
              label: t("servers.actions.move"),
              icon: ArrowRightLeft,
              onPress: () => open("move", server),
            },
            {
              label: t("servers.actions.stop"),
              icon: Square,
              onPress: () => open("stop", server),
            },
          ],
        }
      : {}),
    menu: [
      [
        {
          label: t("servers.actions.open"),
          icon: ArrowUpRight,
          onSelect: () =>
            navigate({
              to: "/servers/$serverId",
              params: { serverId: server.id },
            }),
        },
        ...(can.command
          ? [
              item("command", server, SquareTerminal),
              item("move", server, ArrowRightLeft),
            ]
          : []),
      ],
      ...(can.push ? [[item("push", server, Upload)]] : []),
      ...(can.command
        ? [[item("stop", server, Square), item("kill", server, OctagonX, true)]]
        : []),
    ],
  });

  const server = pending.target;
  const name = server?.displayName ?? "";
  const confirmed =
    (message: string) =>
    (call: Promise<unknown>): Promise<void> =>
      call.then(() => toast({ message }), keepOpen);

  const dialogs =
    server === undefined ? null : (
      <>
        <ConfirmDialog
          open={isOpen("stop")}
          onOpenChange={close}
          title={t("servers.confirm.stopTitle", { name })}
          description={t("servers.confirm.stopDescription")}
          confirmLabel={t("servers.actions.stop")}
          onConfirm={() =>
            confirmed(t("servers.actions.stopping", { name }))(
              stop.mutateAsync({ id: server.id }),
            )
          }
        />
        <ConfirmDialog
          open={isOpen("kill")}
          onOpenChange={close}
          title={t("servers.confirm.killTitle", { name })}
          description={t("servers.confirm.killDescription")}
          confirmLabel={t("servers.actions.kill")}
          typedName={name}
          onConfirm={() =>
            confirmed(t("servers.actions.killed", { name }))(
              kill.mutateAsync({ id: server.id }),
            )
          }
        />
        <ConfirmDialog
          open={isOpen("push")}
          onOpenChange={close}
          title={t("servers.confirm.pushTitle", { name })}
          description={
            server.groupType === "STATIC"
              ? t("servers.confirm.pushStatic", { group: server.groupId })
              : t("servers.confirm.pushDynamic", { group: server.groupId })
          }
          confirmLabel={t("servers.actions.push")}
          onConfirm={() =>
            confirmed(t("servers.actions.pushing", { name }))(
              push.mutateAsync({ id: server.id }),
            )
          }
        />
        <MovePlayersDialog
          open={isOpen("move")}
          onOpenChange={close}
          server={{ id: server.id, name, groupId: server.groupId }}
          groupIds={groupIds}
        />
        <CommandDialog
          open={isOpen("command")}
          onOpenChange={close}
          target={{ kind: "server", id: server.id, name }}
        />
      </>
    );
  return { actionsFor, open, can, dialogs };
}
