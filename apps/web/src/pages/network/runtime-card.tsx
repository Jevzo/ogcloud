import type { RuntimeProfileView } from "@ogcloud/api-client";
import { useRuntimeList, useRuntimeRollout } from "@ogcloud/api-client/react";
import {
  Card,
  ConfirmDialog,
  columnsFor,
  DataTable,
  EmptyState,
  useToast,
} from "@ogcloud/ui";
import { Package, RefreshCw } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { loadFailure, useFailureToast } from "../../errors.ts";
import { usePendingAction } from "../../pending-action.ts";
import { useCan } from "../../permissions.ts";

const columns = columnsFor<RuntimeProfileView>();

/** The runtimes groups can run, with their bundles; a rollout replaces the profile's servers onto a fresh bundle. */
export function RuntimeCard() {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const canWrite = useCan("network:write");
  const runtime = useRuntimeList();
  const rollout = useRuntimeRollout();
  const pending = usePendingAction<"rollout", string>();
  const id = pending.target ?? "";

  const profileColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("id", {
          header: () => t("network.runtime.profile"),
          cell: (cell) => (
            <span className="font-mono text-mono font-label">
              {cell.getValue()}
            </span>
          ),
        }),
        columns.accessor("version", {
          header: () => t("network.runtime.version"),
        }),
        columns.accessor("javaVersion", {
          header: () => t("network.runtime.java"),
        }),
        columns.accessor((profile) => profile.artifacts.length, {
          id: "artifacts",
          header: () => t("network.runtime.bundle"),
          cell: (cell) =>
            t("network.runtime.files", { count: cell.getValue() }),
        }),
        columns.accessor(
          (profile) =>
            profile.artifacts
              .map((artifact) => artifact.storedAt)
              .sort()
              .at(-1) ?? null,
          {
            id: "stored",
            header: () => t("network.runtime.stored"),
            cell: (cell) => <Ago at={cell.getValue()} />,
          },
        ),
      ]),
    [t],
  );

  return (
    <Card flush title={t("network.runtime.title")}>
      <p className="mb-2 px-4 text-small text-text-3">
        {t("network.runtime.description")}
      </p>
      <DataTable
        label={t("network.runtime.title")}
        data={runtime.data?.data ?? []}
        failure={loadFailure(runtime)}
        columns={profileColumns}
        getRowId={(profile) => profile.id}
        actions={
          canWrite
            ? (profile) => ({
                inline: [
                  {
                    label: t("network.runtime.rollout"),
                    icon: RefreshCw,
                    onPress: () => pending.open("rollout", profile.id),
                  },
                ],
                menu: [
                  [
                    {
                      label: t("network.runtime.rollout"),
                      icon: RefreshCw,
                      onSelect: () => pending.open("rollout", profile.id),
                    },
                  ],
                ],
              })
            : undefined
        }
        loading={runtime.isPending}
        empty={
          <EmptyState
            icon={Package}
            title={t("network.runtime.empty")}
            description={t("network.runtime.emptyDescription")}
          />
        }
      />
      <ConfirmDialog
        open={pending.isOpen("rollout")}
        onOpenChange={pending.onOpenChange}
        title={t("network.runtime.confirmTitle", { id })}
        description={t("network.runtime.confirmDescription")}
        confirmLabel={t("network.runtime.rollout")}
        onConfirm={() =>
          rollout
            .mutateAsync({ profileId: id, data: {} })
            .then(
              () => toast({ message: t("network.runtime.started", { id }) }),
              failed,
            )
        }
      />
    </Card>
  );
}
