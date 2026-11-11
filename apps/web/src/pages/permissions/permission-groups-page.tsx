import type { PermissionGroupView } from "@ogcloud/api-client";
import {
  getPermissionGroupListQueryKey,
  usePermissionGroupCreate,
  usePermissionGroupList,
} from "@ogcloud/api-client/react";
import {
  Badge,
  Button,
  columnsFor,
  DataTable,
  Dialog,
  EmptyState,
  Field,
  Input,
  PageHeader,
  useToast,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowUpRight, Plus, Shield, Star } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { DialogFooter } from "../../dialog-footer.tsx";
import { useFailureToast } from "../../errors.ts";
import { useCan } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";
import { RankChip } from "./rank-look.tsx";

const columns = columnsFor<PermissionGroupView>();

/** Every permission group, heaviest first, with how its players look in game. */
export function PermissionGroupsPage() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const canWrite = useCan("permissions:write");
  const groups = usePermissionGroupList();
  const [creating, setCreating] = useState(false);
  const rows = [...(groups.data?.data ?? [])].sort(
    (a, b) => b.settings.weight - a.settings.weight,
  );

  const groupColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("settings.name", {
          header: () => t("permissions.columns.name"),
          cell: (cell) => (
            <span className="flex items-center gap-2">
              <Link
                to="/permissions/$permissionGroupId"
                params={{ permissionGroupId: cell.row.original.id }}
                className="font-label hover:underline"
              >
                {cell.getValue()}
              </Link>
              {cell.row.original.settings.defaultGroup ? (
                <Badge tone="accent" icon={Star}>
                  {t("permissions.default")}
                </Badge>
              ) : null}
            </span>
          ),
        }),
        columns.accessor("id", {
          header: () => t("permissions.columns.id"),
          cell: (cell) => (
            <span className="font-mono text-mono text-text-2">
              {cell.getValue()}
            </span>
          ),
        }),
        columns.display({
          id: "look",
          header: () => t("permissions.columns.look"),
          cell: (cell) => (
            <RankChip display={cell.row.original.settings.display} />
          ),
        }),
        columns.accessor("settings.weight", {
          header: () => t("permissions.columns.weight"),
        }),
        columns.accessor(
          (group) => Object.keys(group.settings.permissions).length,
          {
            id: "nodes",
            header: () => t("permissions.columns.nodes"),
          },
        ),
      ]),
    [t],
  );

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("nav.permissions")}
        description={t("permissions.description")}
        actions={
          canWrite ? (
            <Button
              kind="primary"
              icon={Plus}
              onClick={() => setCreating(true)}
            >
              {t("permissions.create.title")}
            </Button>
          ) : undefined
        }
      />
      <section className="rounded-card border border-border-subtle bg-surface py-1 shadow-card">
        <DataTable
          label={t("nav.permissions")}
          data={rows}
          columns={groupColumns}
          getRowId={(group) => group.id}
          actions={(group) => ({
            menu: [
              [
                {
                  label: t("permissions.open"),
                  icon: ArrowUpRight,
                  onSelect: () =>
                    navigate({
                      to: "/permissions/$permissionGroupId",
                      params: { permissionGroupId: group.id },
                    }),
                },
              ],
            ],
          })}
          loading={groups.isPending}
          empty={
            <EmptyState
              icon={Shield}
              title={t("permissions.empty")}
              description={t("permissions.emptyDescription")}
            />
          }
        />
      </section>
      <CreateDialog open={creating} onOpenChange={setCreating} />
    </div>
  );
}

const ID = /^[a-z0-9][a-z0-9-]*$/;

/** A new permission group needs only an id and a name; everything else is set on its page. */
function CreateDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [id, setId] = useState("");
  const [name, setName] = useState("");
  const create = usePermissionGroupCreate({
    mutation: {
      onSettled: () =>
        invalidatePaths(client, [getPermissionGroupListQueryKey()[0]]),
    },
  });
  const ready = ID.test(id) && name.trim() !== "";

  const submit = () =>
    create.mutate(
      {
        data: {
          id,
          settings: {
            name: name.trim(),
            weight: 0,
            defaultGroup: false,
            display: {
              chatPrefix: "",
              chatSuffix: "",
              nameFormat: "{name}",
              tabPrefix: "",
            },
            permissions: {},
          },
        },
      },
      {
        onSuccess: () => {
          toast({
            message: t("permissions.create.done", { name: name.trim() }),
          });
          onOpenChange(false);
          void navigate({
            to: "/permissions/$permissionGroupId",
            params: { permissionGroupId: id },
          });
        },
        onError: failed,
      },
    );

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("permissions.create.title")}
      description={t("permissions.create.description")}
      footer={
        <DialogFooter
          onCancel={() => onOpenChange(false)}
          submitLabel={t("permissions.create.submit")}
          onSubmit={submit}
          disabled={!ready}
          busy={create.isPending}
        />
      }
    >
      <Field label={t("permissions.form.name")}>
        {(control) => (
          <Input
            {...control}
            value={name}
            placeholder="VIP"
            onChange={(event) => setName(event.target.value)}
          />
        )}
      </Field>
      <Field
        label={t("permissions.form.id")}
        hint={t("permissions.form.idHint")}
      >
        {(control) => (
          <Input
            {...control}
            mono
            value={id}
            placeholder="vip"
            onChange={(event) => setId(event.target.value)}
          />
        )}
      </Field>
    </Dialog>
  );
}
