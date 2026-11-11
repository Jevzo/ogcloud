import { getNpcListQueryKey, useNpcDelete } from "@ogcloud/api-client/react";
import { ConfirmDialog, useToast } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { usePendingAction } from "../../pending-action.ts";
import { invalidatePaths } from "../../queries.ts";

// NPCs have no name; the start of their id is what the confirmation asks for.
const SHORT_ID = 8;

/** Deleting an NPC takes it off every server of its group for good, so its id is typed first (D-0210). */
export function useDeleteNpc(onDeleted?: () => void): {
  ask: (id: string) => void;
  dialog: ReactNode;
} {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const client = useQueryClient();
  const pending = usePendingAction<"delete", string>();
  const remove = useNpcDelete({
    mutation: {
      onSettled: () => invalidatePaths(client, [getNpcListQueryKey()[0]]),
    },
  });
  const id = pending.target ?? "";
  return {
    ask: (target) => pending.open("delete", target),
    dialog: (
      <ConfirmDialog
        open={pending.isOpen("delete")}
        onOpenChange={pending.onOpenChange}
        title={t("npcs.delete.title")}
        description={t("npcs.delete.description")}
        confirmLabel={t("npcs.delete.action")}
        typedName={id.slice(0, SHORT_ID)}
        onConfirm={() =>
          remove.mutateAsync({ id }).then(() => {
            toast({ message: t("npcs.delete.done") });
            onDeleted?.();
          }, failed)
        }
      />
    ),
  };
}
