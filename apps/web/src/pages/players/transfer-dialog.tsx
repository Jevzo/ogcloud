import { usePlayerTransfer, useServerList } from "@ogcloud/api-client/react";
import {
  Button,
  Combobox,
  Dialog,
  Field,
  Segmented,
  useToast,
} from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { GroupSelectField } from "../groups/group-select-field.tsx";

type Target = "group" | "server";

interface TransferDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  player: { uuid: string; name: string };
  groupIds: readonly string[];
}

/** Moves one player to a group, placed by the routing strategy, or to one server (D-0101). */
export function TransferDialog({
  open,
  onOpenChange,
  player,
  groupIds,
}: TransferDialogProps) {
  const { t } = useTranslation("web");
  const { t: ui } = useTranslation("ui");
  const toast = useToast();
  const failed = useFailureToast();
  const servers = useServerList();
  const [target, setTarget] = useState<Target>("group");
  const [group, setGroup] = useState<string>();
  const [server, setServer] = useState<string>();
  const transfer = usePlayerTransfer();
  const chosen = target === "group" ? group : server;

  const submit = () => {
    if (chosen === undefined) return;
    transfer.mutate(
      {
        uuid: player.uuid,
        data: target === "group" ? { groupId: chosen } : { serverId: chosen },
      },
      {
        onSuccess: () => {
          toast({ message: t("players.transfer.done", { name: player.name }) });
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
      title={t("players.transfer.title", { name: player.name })}
      description={t("players.transfer.description")}
      footer={
        <>
          <Button kind="secondary" onClick={() => onOpenChange(false)}>
            {ui("actions.cancel")}
          </Button>
          <Button
            kind="primary"
            onClick={submit}
            disabled={chosen === undefined}
            loading={transfer.isPending}
          >
            {t("players.transfer.submit")}
          </Button>
        </>
      }
    >
      <Segmented
        label={t("players.transfer.to")}
        options={[
          { value: "group", label: t("players.transfer.group") },
          { value: "server", label: t("players.transfer.server") },
        ]}
        value={target}
        onValueChange={setTarget}
      />
      {target === "group" ? (
        <GroupSelectField
          label={t("players.transfer.group")}
          groupIds={groupIds}
          value={group}
          onValueChange={setGroup}
        />
      ) : (
        <Field label={t("players.transfer.server")}>
          {(control) => (
            <Combobox
              {...control}
              mono
              options={(servers.data?.data ?? [])
                .filter((candidate) => candidate.groupType !== "PROXY")
                .map((candidate) => ({
                  value: candidate.id,
                  label: candidate.displayName,
                  hint: candidate.groupId,
                }))}
              value={server}
              onValueChange={setServer}
              placeholder={t("players.transfer.pickServer")}
            />
          )}
        </Field>
      )}
    </Dialog>
  );
}
