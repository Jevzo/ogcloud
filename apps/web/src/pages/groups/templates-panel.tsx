import {
  type GroupViewType,
  getTemplateDownloadUrl,
  getTemplateUploadUrl,
  type TemplateVersionView,
} from "@ogcloud/api-client";
import {
  ApiConnectionContext,
  getGroupGetQueryKey,
  getTemplateListQueryKey,
  useTemplateDelete,
  useTemplateList,
  useTemplatePromote,
} from "@ogcloud/api-client/react";
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  columnsFor,
  DataTable,
  EmptyState,
  Note,
  useToast,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import {
  CircleCheck,
  Download,
  FileArchive,
  Trash2,
  TriangleAlert,
  Undo2,
  Upload,
} from "lucide-react";
import { type ChangeEvent, useContext, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { agoColumn, monoColumn } from "../../columns.tsx";
import {
  loadFailure,
  useConfirmFailure,
  useFailureToast,
} from "../../errors.ts";
import { downloadFile, uploadWithProgress } from "../../files.ts";
import { useFormat } from "../../format.ts";
import { usePendingAction } from "../../pending-action.ts";
import { useCan } from "../../permissions.ts";
import { placeholderTemplate } from "../../placeholders.ts";
import { invalidatePaths } from "../../queries.ts";

const GZIP = "application/gzip";
const SHORT_HASH = 12;
const columns = columnsFor<TemplateVersionView>();

interface TemplatesPanelProps {
  groupId: string;
  type: GroupViewType;
  /** A running STATIC server overwrites the active version when it stops (D-0123). */
  staticRunning: boolean;
}

/** A group's template versions (D-0042): upload a new one, make one active, download or delete the rest. */
export function TemplatesPanel({
  groupId,
  type,
  staticRunning,
}: TemplatesPanelProps) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const keepOpen = useConfirmFailure();
  const format = useFormat();
  const client = useQueryClient();
  const connection = useContext(ApiConnectionContext);
  const canWrite = useCan("templates:write");
  const versions = useTemplateList(groupId);
  const input = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const pending = usePendingAction<
    "activate" | "delete",
    TemplateVersionView
  >();
  const refresh = () =>
    invalidatePaths(client, [
      getTemplateListQueryKey(groupId)[0],
      getGroupGetQueryKey(groupId)[0],
    ]);
  const promote = useTemplatePromote({ mutation: { onSettled: refresh } });
  const remove = useTemplateDelete({ mutation: { onSettled: refresh } });
  const rows = [...(versions.data?.data ?? [])].sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt),
  );
  const active = rows.find((version) => version.active);
  const short = (version: TemplateVersionView) =>
    version.sha256.slice(0, SHORT_HASH);

  const upload = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (file === undefined || connection === null) return;
    setProgress(0);
    uploadWithProgress<TemplateVersionView>(
      connection,
      getTemplateUploadUrl(groupId),
      file,
      GZIP,
      (sent, total) => setProgress(total === 0 ? 0 : sent / total),
    )
      .then(
        () =>
          toast({
            message:
              type === "STATIC"
                ? t("templates.uploadedActive")
                : t("templates.uploaded"),
          }),
        failed,
      )
      .finally(() => {
        setProgress(null);
        void refresh();
      });
  };
  const download = (version: TemplateVersionView) => {
    if (connection === null) return;
    downloadFile(
      connection,
      getTemplateDownloadUrl(groupId, version.id),
      `${groupId}-${short(version)}.tar.gz`,
    ).catch(failed);
  };

  const versionColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("sha256", {
          header: () => t("templates.columns.version"),
          cell: (cell) => (
            <span className="flex items-center gap-2">
              <span className="font-mono text-mono">
                {cell.getValue().slice(0, SHORT_HASH)}
              </span>
              {cell.row.original.active ? (
                <Badge tone="success" icon={CircleCheck}>
                  {t("templates.active")}
                </Badge>
              ) : null}
            </span>
          ),
        }),
        columns.accessor("source", {
          header: () => t("templates.columns.source"),
          cell: (cell) => (
            <span className="text-text-2">
              {t(`templates.source.${cell.getValue()}`)}
            </span>
          ),
        }),
        columns.accessor("sizeBytes", {
          header: () => t("templates.columns.size"),
          meta: { numeric: true },
          cell: (cell) => (
            <span className="text-text-2">{format.bytes(cell.getValue())}</span>
          ),
        }),
        agoColumn(
          columns,
          "createdAt",
          t("templates.columns.created"),
          (version) => version.createdAt,
        ),
        monoColumn(
          columns,
          "createdBy",
          t("templates.columns.by"),
          (version) => version.createdBy,
        ),
      ]),
    [t, format],
  );

  return (
    <div className="flex flex-col gap-4">
      {type === "STATIC" && staticRunning ? (
        <Note tone="warning" icon={TriangleAlert}>
          {t("templates.staticWarning")}
        </Note>
      ) : null}
      <Card
        flush
        title={t("templates.title")}
        actions={
          canWrite ? (
            <>
              <input
                ref={input}
                type="file"
                accept=".tar.gz,.tgz,application/gzip"
                hidden
                onChange={upload}
              />
              <Button
                size="sm"
                icon={Upload}
                loading={progress !== null}
                onClick={() => input.current?.click()}
              >
                {progress === null
                  ? t("templates.upload")
                  : t("templates.uploading", {
                      percent: Math.round(progress * 100),
                    })}
              </Button>
            </>
          ) : undefined
        }
      >
        <p className="mb-2 px-4 text-small text-text-3">
          {type === "STATIC"
            ? t("templates.descriptionStatic")
            : t("templates.descriptionDynamic")}
        </p>
        <DataTable
          label={t("templates.title")}
          data={rows}
          failure={loadFailure(versions)}
          columns={versionColumns}
          getRowId={(version) => version.id}
          placeholder={placeholderTemplate}
          actions={(version) => ({
            inline: [
              {
                label: t("templates.download"),
                icon: Download,
                onPress: () => download(version),
              },
              ...(canWrite && !version.active
                ? [
                    {
                      label: t("templates.activate"),
                      icon: Undo2,
                      onPress: () => pending.open("activate", version),
                    },
                  ]
                : []),
            ],
            menu: [
              [
                {
                  label: t("templates.download"),
                  icon: Download,
                  onSelect: () => download(version),
                },
                ...(canWrite && !version.active
                  ? [
                      {
                        label: t("templates.activate"),
                        icon: Undo2,
                        onSelect: () => pending.open("activate", version),
                      },
                    ]
                  : []),
              ],
              ...(canWrite && !version.active
                ? [
                    [
                      {
                        label: t("templates.delete"),
                        icon: Trash2,
                        danger: true,
                        onSelect: () => pending.open("delete", version),
                      },
                    ],
                  ]
                : []),
            ],
          })}
          loading={versions.isPending}
          empty={
            <EmptyState
              icon={FileArchive}
              title={t("templates.empty")}
              description={t("templates.emptyDescription")}
            />
          }
        />
      </Card>
      <ConfirmDialog
        open={pending.isOpen("activate")}
        onOpenChange={pending.onOpenChange}
        title={t("templates.activateTitle", {
          version: pending.target === undefined ? "" : short(pending.target),
        })}
        description={
          active !== undefined &&
          pending.target !== undefined &&
          pending.target.createdAt < active.createdAt
            ? t("templates.rollbackDescription")
            : t("templates.activateDescription")
        }
        confirmLabel={t("templates.activate")}
        onConfirm={() =>
          pending.target === undefined
            ? undefined
            : promote
                .mutateAsync({
                  groupId,
                  data: { versionId: pending.target.id },
                })
                .then(
                  () => toast({ message: t("templates.activated") }),
                  keepOpen,
                )
        }
      />
      <ConfirmDialog
        open={pending.isOpen("delete")}
        onOpenChange={pending.onOpenChange}
        title={t("templates.deleteTitle")}
        description={t("templates.deleteDescription")}
        confirmLabel={t("templates.delete")}
        typedName={pending.target === undefined ? "" : short(pending.target)}
        onConfirm={() =>
          pending.target === undefined
            ? undefined
            : remove
                .mutateAsync({ groupId, versionId: pending.target.id })
                .then(
                  () => toast({ message: t("templates.deleted") }),
                  keepOpen,
                )
        }
      />
    </div>
  );
}
