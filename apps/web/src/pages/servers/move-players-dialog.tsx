import { useServerTransferAll } from "@ogcloud/api-client/react";
import { Button, Dialog, Field, Select, useToast } from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";

interface MovePlayersDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  server: { id: string; name: string; groupId: string };
  groupIds: readonly string[];
}

/** Sends everyone on a server to another group, routed by the network's strategy (D-0101). */
export function MovePlayersDialog({
  open,
  onOpenChange,
  server,
  groupIds,
}: MovePlayersDialogProps) {
  const { t } = useTranslation("web");
  const { t: ui } = useTranslation("ui");
  const toast = useToast();
  const failed = useFailureToast();
  const [group, setGroup] = useState<string>();
  const move = useServerTransferAll();

  const submit = () => {
    if (group === undefined) return;
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
    );
  };

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("servers.move.title", { name: server.name })}
      description={t("servers.move.description")}
      footer={
        <>
          <Button kind="secondary" onClick={() => onOpenChange(false)}>
            {ui("actions.cancel")}
          </Button>
          <Button
            kind="primary"
            onClick={submit}
            disabled={group === undefined}
            loading={move.isPending}
          >
            {t("servers.move.submit")}
          </Button>
        </>
      }
    >
      <Field label={t("servers.move.target")}>
        {(control) => (
          <Select
            {...control}
            options={groupIds
              .filter((id) => id !== server.groupId)
              .map((id) => ({ value: id, label: id }))}
            value={group}
            onValueChange={setGroup}
            placeholder={t("servers.request.pickGroup")}
          />
        )}
      </Field>
    </Dialog>
  );
}
