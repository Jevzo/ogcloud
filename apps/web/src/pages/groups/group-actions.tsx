import {
  getGroupListQueryKey,
  getJobListQueryKey,
  getOverviewGetQueryKey,
  useCrashLoopResumeHook,
  useGroupGetHook,
  useGroupRestartHook,
  useGroupUpdateHook,
} from "@ogcloud/api-client/react";
import { ConfirmDialog, type RowActions, useToast } from "@ogcloud/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Construction, Play, RotateCw } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { usePendingAction } from "../../pending-action.ts";
import { useCan } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";

/** What the actions need to know about a group, from the overview or the groups list. */
export interface GroupState {
  id: string;
  maintenance: boolean;
  crashLoopPaused: boolean;
}

function useGroupMutations() {
  const read = useGroupGetHook();
  const update = useGroupUpdateHook();
  const restart = useGroupRestartHook();
  const resume = useCrashLoopResumeHook();
  const client = useQueryClient();
  const refresh = () =>
    invalidatePaths(client, [
      getGroupListQueryKey()[0],
      getOverviewGetQueryKey()[0],
      getJobListQueryKey()[0],
    ]);
  return {
    maintenance: useMutation({
      mutationFn: async (change: { id: string; maintenance: boolean }) => {
        const { data } = await read(change.id);
        await update(change.id, {
          settings: { ...data.settings, maintenance: change.maintenance },
          version: data.version,
        });
      },
      onSettled: refresh,
    }),
    restart: useMutation({
      mutationFn: (id: string) => restart(id, {}),
      onSettled: refresh,
    }),
    resume: useMutation({
      mutationFn: (id: string) => resume(id),
      onSettled: refresh,
    }),
  };
}

/**
 * A group's actions as table row actions, with the confirmations they need (D-0210); the overview and the groups
 * list share them. Restarting needs maintenance first, as the API requires.
 */
export function useGroupActions(): {
  /** Undefined without `groups:write`, so tables leave out the actions column. */
  actionsFor: ((group: GroupState) => RowActions) | undefined;
  /** Lets a group paused by the crash-loop breaker start servers again. */
  resume: (id: string) => void;
  dialogs: ReactNode;
} {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const canWrite = useCan("groups:write");
  const mutations = useGroupMutations();
  const confirming = usePendingAction<"maintenance" | "restart", string>();

  const endMaintenance = (id: string) =>
    mutations.maintenance.mutate(
      { id, maintenance: false },
      {
        onSuccess: () =>
          toast({ message: t("groups.actions.maintenanceEnded", { id }) }),
        onError: failed,
      },
    );
  const resume = (id: string) =>
    mutations.resume.mutate(id, {
      onSuccess: () => toast({ message: t("groups.actions.resumed", { id }) }),
      onError: failed,
    });

  const actionsOf = (group: GroupState): RowActions => {
    const maintenance = group.maintenance
      ? {
          label: t("groups.actions.endMaintenance"),
          icon: Play,
          onPress: () => endMaintenance(group.id),
        }
      : {
          label: t("groups.actions.startMaintenance"),
          icon: Construction,
          onPress: () => confirming.open("maintenance", group.id),
        };
    const restart = {
      label: t("groups.actions.restart"),
      icon: RotateCw,
      onSelect: () => confirming.open("restart", group.id),
    };
    return {
      inline: [maintenance],
      menu: [
        [
          { ...maintenance, onSelect: maintenance.onPress },
          ...(group.maintenance ? [restart] : []),
        ],
        ...(group.crashLoopPaused
          ? [
              [
                {
                  label: t("groups.actions.resume"),
                  icon: Play,
                  onSelect: () => resume(group.id),
                },
              ],
            ]
          : []),
      ],
    };
  };

  const actionsFor = canWrite ? actionsOf : undefined;
  const id = confirming.target ?? "";
  const dialogs = (
    <>
      <ConfirmDialog
        open={confirming.isOpen("maintenance")}
        onOpenChange={confirming.onOpenChange}
        title={t("groups.confirm.maintenanceTitle", { id })}
        description={t("groups.confirm.maintenanceDescription", { id })}
        confirmLabel={t("groups.actions.startMaintenance")}
        onConfirm={() =>
          mutations.maintenance.mutateAsync({ id, maintenance: true }).then(
            () =>
              toast({
                message: t("groups.actions.maintenanceStarted", { id }),
              }),
            failed,
          )
        }
      />
      <ConfirmDialog
        open={confirming.isOpen("restart")}
        onOpenChange={confirming.onOpenChange}
        title={t("groups.confirm.restartTitle", { id })}
        description={t("groups.confirm.restartDescription", { id })}
        confirmLabel={t("groups.actions.restart")}
        onConfirm={() =>
          mutations.restart
            .mutateAsync(id)
            .then(
              () => toast({ message: t("groups.actions.restarting", { id }) }),
              failed,
            )
        }
      />
    </>
  );
  return { actionsFor, resume, dialogs };
}
