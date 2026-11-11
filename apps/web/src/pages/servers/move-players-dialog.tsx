import { useServerTransferAll } from "@ogcloud/api-client/react";
import { useToast } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { GroupPickerDialog } from "../groups/group-picker-dialog.tsx";

interface MovePlayersDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  server: { id: string; name: string; groupId: string };
  groupIds: readonly string[];
}

/** Sends everyone on a server to another group, placed by the network's routing strategy (D-0101). */
export function MovePlayersDialog({
  open,
  onOpenChange,
  server,
  groupIds,
}: MovePlayersDialogProps) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const move = useServerTransferAll();

  return (
    <GroupPickerDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("servers.move.title", { name: server.name })}
      description={t("servers.move.description")}
      fieldLabel={t("servers.move.target")}
      submitLabel={t("servers.move.submit")}
      groupIds={groupIds.filter((id) => id !== server.groupId)}
      busy={move.isPending}
      onPick={(group) =>
        move.mutate(
          { id: server.id, data: { groupId: group } },
          {
            onSuccess: () => {
              toast({
                message: t("servers.move.done", { name: server.name, group }),
              });
              onOpenChange(false);
            },
            onError: failed,
          },
        )
      }
    />
  );
}
