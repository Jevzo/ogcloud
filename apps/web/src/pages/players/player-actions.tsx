import { useOverviewGet } from "@ogcloud/api-client/react";
import type { RowActions } from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import { ArrowRightLeft, ArrowUpRight, Crown, LogOut } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { usePendingAction } from "../../pending-action.ts";
import { useCan } from "../../permissions.ts";
import { KickDialog } from "./kick-dialog.tsx";
import { RankDialog } from "./rank-dialog.tsx";
import { TransferDialog } from "./transfer-dialog.tsx";

/** What the actions need to know about a player. */
export interface PlayerRef {
  uuid: string;
  name: string;
  permissionGroupId: string;
  online: boolean;
}

export type PlayerAction = "rank" | "transfer" | "kick";

/** A player's actions with their dialogs: a rank with an end, a move, a kick with a message (D-0210). */
export function usePlayerActions(): {
  actionsFor: (player: PlayerRef) => RowActions;
  open: (action: PlayerAction, player: PlayerRef) => void;
  canWrite: boolean;
  dialogs: ReactNode;
} {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const canWrite = useCan("players:write");
  const overview = useOverviewGet();
  const groupIds = (overview.data?.data.groups ?? [])
    .filter((group) => group.type !== "PROXY")
    .map((group) => group.id);
  const pending = usePendingAction<PlayerAction, PlayerRef>();

  const actionsFor = (player: PlayerRef): RowActions => {
    const transfer = {
      label: t("players.actions.transfer"),
      icon: ArrowRightLeft,
      onPress: () => pending.open("transfer", player),
    };
    const rank = {
      label: t("players.actions.rank"),
      icon: Crown,
      onPress: () => pending.open("rank", player),
    };
    const live = canWrite && player.online;
    return {
      ...(canWrite ? { inline: live ? [transfer, rank] : [rank] } : {}),
      menu: [
        [
          {
            label: t("players.actions.open"),
            icon: ArrowUpRight,
            onSelect: () =>
              navigate({
                to: "/players/$playerId",
                params: { playerId: player.uuid },
              }),
          },
        ],
        ...(canWrite
          ? [
              [
                ...(live ? [{ ...transfer, onSelect: transfer.onPress }] : []),
                { ...rank, onSelect: rank.onPress },
              ],
            ]
          : []),
        ...(live
          ? [
              [
                {
                  label: t("players.actions.kick"),
                  icon: LogOut,
                  danger: true,
                  onSelect: () => pending.open("kick", player),
                },
              ],
            ]
          : []),
      ],
    };
  };

  const player = pending.target;
  const dialogs =
    player === undefined ? null : (
      <>
        <RankDialog
          key={`rank-${player.uuid}`}
          open={pending.isOpen("rank")}
          onOpenChange={pending.onOpenChange}
          player={player}
        />
        <TransferDialog
          key={`transfer-${player.uuid}`}
          open={pending.isOpen("transfer")}
          onOpenChange={pending.onOpenChange}
          player={player}
          groupIds={groupIds}
        />
        <KickDialog
          key={`kick-${player.uuid}`}
          open={pending.isOpen("kick")}
          onOpenChange={pending.onOpenChange}
          player={player}
        />
      </>
    );
  return { actionsFor, open: pending.open, canWrite, dialogs };
}
