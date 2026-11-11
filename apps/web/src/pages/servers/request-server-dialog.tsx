import {
  getOverviewGetQueryKey,
  getServerListQueryKey,
  useServerRequest,
} from "@ogcloud/api-client/react";
import { useToast } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { invalidatePaths } from "../../queries.ts";
import { GroupPickerDialog } from "../groups/group-picker-dialog.tsx";

interface RequestServerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  groupIds: readonly string[];
}

/** Asks the controller for one more server of a group; it starts when the provisioner gets to it (D-0075). */
export function RequestServerDialog({
  open,
  onOpenChange,
  groupIds,
}: RequestServerDialogProps) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const client = useQueryClient();
  const request = useServerRequest({
    mutation: {
      onSettled: () =>
        invalidatePaths(client, [
          getServerListQueryKey()[0],
          getOverviewGetQueryKey()[0],
        ]),
    },
  });

  return (
    <GroupPickerDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("servers.request.title")}
      description={t("servers.request.description")}
      fieldLabel={t("servers.request.group")}
      submitLabel={t("servers.request.submit")}
      groupIds={groupIds}
      busy={request.isPending}
      onPick={(group) =>
        request.mutate(
          { data: { groupId: group } },
          {
            onSuccess: () => {
              toast({ message: t("servers.request.done", { group }) });
              onOpenChange(false);
            },
            onError: failed,
          },
        )
      }
    />
  );
}
