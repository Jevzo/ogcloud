import type { PermissionGroupView } from "@ogcloud/api-client";
import {
  getPermissionGroupListQueryKey,
  usePermissionGroupCreate,
  usePermissionGroupList,
} from "@ogcloud/api-client/react";
import { PermissionGroupCreateBody } from "@ogcloud/api-client/zod";
import {
  Badge,
  Button,
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  PageHeader,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowUpRight, Plus, Shield, Star } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { monoColumn } from "../../columns.tsx";
import { loadFailure } from "../../errors.ts";
import { NameIdDialog } from "../../name-id-dialog.tsx";
import { useCan } from "../../permissions.ts";
import { placeholderPermissionGroup } from "../../placeholders.ts";
import { invalidatePaths } from "../../queries.ts";
import { PermissionGroupDrawer } from "./permission-group-drawer.tsx";
import { RankChip } from "./rank-look.tsx";

const columns = columnsFor<PermissionGroupView>();

/** Every permission group, heaviest first, with how its players look in game. */
export function PermissionGroupsPage() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const [opened, setOpened] = useState<PermissionGroupView | null>(null);
  const canWrite = useCan("permissions:write");
  const groups = usePermissionGroupList();
  const queries = useQueryClient();
  const [creating, setCreating] = useState(false);
  const create = usePermissionGroupCreate({
    mutation: {
      onSettled: () =>
        invalidatePaths(queries, [getPermissionGroupListQueryKey()[0]]),
    },
  });
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
                <Badge tone="highlight" icon={Star}>
                  {t("permissions.default")}
                </Badge>
              ) : null}
            </span>
          ),
        }),
        monoColumn(
          columns,
          "id",
          t("permissions.columns.id"),
          (group) => group.id,
        ),
        columns.display({
          id: "look",
          header: () => t("permissions.columns.look"),
          cell: (cell) => (
            <RankChip display={cell.row.original.settings.display} />
          ),
        }),
        columns.accessor("settings.weight", {
          header: () => t("permissions.columns.weight"),
          meta: { numeric: true },
        }),
        columns.accessor(
          (group) => Object.keys(group.settings.permissions).length,
          {
            id: "nodes",
            header: () => t("permissions.columns.nodes"),
            meta: { numeric: true },
          },
        ),
      ]),
    [t],
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("nav.permissions")}
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
      <Card flush>
        <DataTable
          label={t("nav.permissions")}
          data={rows}
          failure={loadFailure(groups)}
          columns={groupColumns}
          getRowId={(group) => group.id}
          placeholder={placeholderPermissionGroup}
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
          onRowOpen={setOpened}
          loading={groups.isPending}
          empty={
            <EmptyState
              icon={Shield}
              title={t("permissions.empty")}
              description={t("permissions.emptyDescription")}
            />
          }
        />
      </Card>
      <PermissionGroupDrawer group={opened} onClose={() => setOpened(null)} />
      <NameIdDialog
        open={creating}
        onOpenChange={setCreating}
        title={t("permissions.create.title")}
        description={t("permissions.create.description")}
        submitLabel={t("permissions.create.submit")}
        name={{ label: t("permissions.form.name"), placeholder: "VIP" }}
        id={{
          label: t("permissions.form.id"),
          hint: t("permissions.form.idHint"),
          placeholder: "vip",
          schema: PermissionGroupCreateBody.shape.id,
        }}
        create={(id, name) =>
          create.mutateAsync({ data: { id, settings: blankGroup(name) } })
        }
        done={(name) => t("permissions.create.done", { name })}
        onCreated={(id) =>
          void navigate({
            to: "/permissions/$permissionGroupId",
            params: { permissionGroupId: id },
          })
        }
      />
    </div>
  );
}

/** A new permission group starts plain: no permissions, the player's name as it is. */
const blankGroup = (name: string) => ({
  name,
  weight: 0,
  defaultGroup: false,
  display: {
    chatPrefix: "",
    chatSuffix: "",
    nameFormat: "{name}",
    tabPrefix: "",
  },
  permissions: {},
});
