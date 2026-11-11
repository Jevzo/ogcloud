import {
  getOverviewGetQueryKey,
  getServerListQueryKey,
  useServerRequest,
} from "@ogcloud/api-client/react";
import { Button, Dialog, Field, Select, useToast } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { invalidatePaths } from "../../queries.ts";

interface RequestServerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  groupIds: readonly string[];
  /** Picked when the dialog opens, e.g. from a group's page. */
  initialGroup?: string;
}

/** Asks the controller for one more server of a group; it starts when the provisioner gets to it (D-0075). */
export function RequestServerDialog({
  open,
  onOpenChange,
  groupIds,
  initialGroup,
}: RequestServerDialogProps) {
  const { t } = useTranslation("web");
  const { t: ui } = useTranslation("ui");
  const toast = useToast();
  const failed = useFailureToast();
  const client = useQueryClient();
  const [group, setGroup] = useState(initialGroup);
  const request = useServerRequest({
    mutation: {
      onSettled: () =>
        invalidatePaths(client, [
          getServerListQueryKey()[0],
          getOverviewGetQueryKey()[0],
        ]),
    },
  });

  const submit = () => {
    if (group === undefined) return;
    request.mutate(
      { data: { groupId: group } },
      {
        onSuccess: () => {
          toast({ message: t("servers.request.done", { group }) });
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
      title={t("servers.request.title")}
      description={t("servers.request.description")}
      footer={
        <>
          <Button kind="secondary" onClick={() => onOpenChange(false)}>
            {ui("actions.cancel")}
          </Button>
          <Button
            kind="primary"
            onClick={submit}
            disabled={group === undefined}
            loading={request.isPending}
          >
            {t("servers.request.submit")}
          </Button>
        </>
      }
    >
      <Field label={t("servers.request.group")}>
        {(control) => (
          <Select
            {...control}
            options={groupIds.map((id) => ({ value: id, label: id }))}
            value={group}
            onValueChange={setGroup}
            placeholder={t("servers.request.pickGroup")}
          />
        )}
      </Field>
    </Dialog>
  );
}
