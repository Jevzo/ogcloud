import {
  getGroupDeletedQueryKey,
  getGroupListQueryKey,
  getJobListQueryKey,
  getOverviewGetQueryKey,
  useCrashLoopResumeHook,
  useGroupDeleteHook,
  useGroupGetHook,
  useGroupRestartHook,
  useGroupRestoreHook,
  useGroupUpdateHook,
} from "@ogcloud/api-client/react";
import {
  ConfirmDialog,
  type MenuItem,
  type RowActions,
  useToast,
} from "@ogcloud/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import {
  ArrowUpRight,
  Construction,
  type LucideIcon,
  Play,
  RotateCw,
  Trash2,
} from "lucide-react";
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

export type GroupAction =
  | "startMaintenance"
  | "endMaintenance"
  | "restart"
  | "resume"
  | "delete"
  | "restore";

/** How a restore ended; it lives on the mutation, so the undo toast still reports after its page is gone. */
interface RestoreOutcome {
  onSuccess: (answer: unknown, id: string) => void;
  onError: (error: unknown) => void;
}

function useGroupMutations(restoreOutcome: RestoreOutcome) {
  const read = useGroupGetHook();
  const update = useGroupUpdateHook();
  const restart = useGroupRestartHook();
  const resume = useCrashLoopResumeHook();
  const remove = useGroupDeleteHook();
  const restore = useGroupRestoreHook();
  const client = useQueryClient();
  const refresh = () =>
    invalidatePaths(client, [
      getGroupListQueryKey()[0],
      getGroupDeletedQueryKey()[0],
      getOverviewGetQueryKey()[0],
      getJobListQueryKey()[0],
    ]);
  const settle = { onSettled: refresh };
  return {
    maintenance: useMutation({
      mutationFn: async (change: { id: string; on: boolean }) => {
        // Read just before writing, so the change carries the current version (D-0069).
        const { data } = await read(change.id);
        await update(change.id, {
          settings: { ...data.settings, maintenance: change.on },
          version: data.version,
        });
      },
      ...settle,
    }),
    restart: useMutation({
      mutationFn: (id: string) => restart(id, {}),
      ...settle,
    }),
    resume: useMutation({ mutationFn: (id: string) => resume(id), ...settle }),
    remove: useMutation({ mutationFn: (id: string) => remove(id), ...settle }),
    restore: useMutation({
      mutationFn: (id: string) => restore(id),
      ...settle,
      ...restoreOutcome,
    }),
  };
}

/**
 * A group's actions with the confirmations they need (D-0210): maintenance asks first, a restart needs maintenance
 * (as the API requires), and a delete can be undone, since deleted groups stay restorable for a while (D-0043). The
 * overview, the groups list and a group's page share them.
 */
export function useGroupActions(options: { onDeleted?: () => void } = {}): {
  actionsFor: (group: GroupState) => RowActions;
  request: (action: GroupAction, id: string) => void;
  canWrite: boolean;
  dialogs: ReactNode;
} {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const navigate = useNavigate();
  const canWrite = useCan("groups:write");
  const mutations = useGroupMutations({
    onSuccess: (_, id) =>
      toast({ message: t("groups.actions.restored", { id }) }),
    onError: failed,
  });
  const confirming = usePendingAction<
    "startMaintenance" | "restart" | "delete",
    string
  >();

  const restore = (id: string) => mutations.restore.mutate(id);
  const request = (action: GroupAction, id: string) => {
    switch (action) {
      case "endMaintenance":
        mutations.maintenance.mutate(
          { id, on: false },
          {
            onSuccess: () =>
              toast({ message: t("groups.actions.maintenanceEnded", { id }) }),
            onError: failed,
          },
        );
        return;
      case "resume":
        mutations.resume.mutate(id, {
          onSuccess: () =>
            toast({ message: t("groups.actions.resumed", { id }) }),
          onError: failed,
        });
        return;
      case "restore":
        restore(id);
        return;
      default:
        confirming.open(action, id);
    }
  };
  const item = (
    action: GroupAction,
    id: string,
    icon: LucideIcon,
    danger = false,
  ): MenuItem => ({
    label: t(`groups.actions.${action}`),
    icon,
    danger,
    onSelect: () => request(action, id),
  });

  const actionsFor = (group: GroupState): RowActions => {
    const [toggle, toggleIcon] = group.maintenance
      ? (["endMaintenance", Play] as const)
      : (["startMaintenance", Construction] as const);
    const maintenance = item(toggle, group.id, toggleIcon);
    return {
      ...(canWrite
        ? {
            inline: [
              {
                label: maintenance.label,
                icon: toggleIcon,
                onPress: maintenance.onSelect,
              },
            ],
          }
        : {}),
      menu: [
        [
          {
            label: t("groups.actions.open"),
            icon: ArrowUpRight,
            onSelect: () =>
              navigate({
                to: "/groups/$groupId",
                params: { groupId: group.id },
              }),
          },
        ],
        ...(canWrite
          ? [
              [
                maintenance,
                ...(group.maintenance
                  ? [item("restart", group.id, RotateCw)]
                  : []),
                ...(group.crashLoopPaused
                  ? [item("resume", group.id, Play)]
                  : []),
              ],
              [item("delete", group.id, Trash2, true)],
            ]
          : []),
      ],
    };
  };

  const id = confirming.target ?? "";
  const done = (message: string) => () => toast({ message });
  const dialogs = (
    <>
      <ConfirmDialog
        open={confirming.isOpen("startMaintenance")}
        onOpenChange={confirming.onOpenChange}
        title={t("groups.confirm.maintenanceTitle", { id })}
        description={t("groups.confirm.maintenanceDescription", { id })}
        confirmLabel={t("groups.actions.startMaintenance")}
        onConfirm={() =>
          mutations.maintenance
            .mutateAsync({ id, on: true })
            .then(done(t("groups.actions.maintenanceStarted", { id })), failed)
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
            .then(done(t("groups.actions.restarting", { id })), failed)
        }
      />
      <ConfirmDialog
        open={confirming.isOpen("delete")}
        onOpenChange={confirming.onOpenChange}
        title={t("groups.confirm.deleteTitle", { id })}
        description={t("groups.confirm.deleteDescription")}
        confirmLabel={t("groups.actions.delete")}
        onConfirm={() =>
          mutations.remove.mutateAsync(id).then(() => {
            toast({
              message: t("groups.actions.deleted", { id }),
              action: {
                label: t("groups.actions.undo"),
                onPress: () => restore(id),
              },
            });
            options.onDeleted?.();
          }, failed)
        }
      />
    </>
  );
  return { actionsFor, request, canWrite, dialogs };
}
