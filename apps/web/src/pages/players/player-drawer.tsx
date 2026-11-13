import { Button } from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import { ArrowRightLeft, Crown, LogOut } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { RecordDrawer } from "../../record-drawer.tsx";
import type {
  PlayerAction,
  PlayerRef,
  usePlayerActions,
} from "./player-actions.tsx";

/** A player's key facts beside the list, their everyday actions and the way to their page (D-0231). */
export function PlayerDrawer({
  player,
  onClose,
  actions,
  children,
}: {
  player: PlayerRef | null;
  onClose: () => void;
  actions: ReturnType<typeof usePlayerActions>;
  /** Detail rows about the player. */
  children: ReactNode;
}) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  if (player === null) return null;
  // The drawer closes first, so the action's dialog is the only layer on top.
  const run = (action: PlayerAction) => {
    onClose();
    actions.open(action, player);
  };
  return (
    <RecordDrawer
      title={player.name}
      onClose={onClose}
      onOpenPage={() =>
        void navigate({
          to: "/players/$playerId",
          params: { playerId: player.uuid },
        })
      }
      actions={
        actions.canWrite ? (
          <>
            {player.online ? (
              <>
                <Button icon={LogOut} onClick={() => run("kick")}>
                  {t("players.actions.kick")}
                </Button>
                <Button icon={ArrowRightLeft} onClick={() => run("transfer")}>
                  {t("players.actions.transfer")}
                </Button>
              </>
            ) : null}
            <Button icon={Crown} onClick={() => run("rank")}>
              {t("players.actions.rank")}
            </Button>
          </>
        ) : null
      }
    >
      {children}
    </RecordDrawer>
  );
}
