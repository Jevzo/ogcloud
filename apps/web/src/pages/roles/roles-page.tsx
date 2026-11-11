import type { RoleView } from "@ogcloud/api-client";
import {
  getRoleListQueryKey,
  useRoleCreate,
  useRoleList,
} from "@ogcloud/api-client/react";
import { RoleCreateBody } from "@ogcloud/api-client/zod";
import {
  Badge,
  Button,
  columnsFor,
  DataTable,
  EmptyState,
  PageHeader,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowUpRight, KeyRound, Lock, Plus } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { monoColumn } from "../../columns.tsx";
import { loadFailure } from "../../errors.ts";
import { NameIdDialog } from "../../name-id-dialog.tsx";
import { useIsOwner } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";

const columns = columnsFor<RoleView>();

/** Every role: the four built in (D-0089) and the network's own, with what each may do. */
export function RolesPage() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const isOwner = useIsOwner();
  const roles = useRoleList();
  const queries = useQueryClient();
  const [creating, setCreating] = useState(false);
  const create = useRoleCreate({
    mutation: {
      onSettled: () => invalidatePaths(queries, [getRoleListQueryKey()[0]]),
    },
  });

  const roleColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("name", {
          header: () => t("roles.columns.name"),
          cell: (cell) => (
            <Link
              to="/roles/$roleId"
              params={{ roleId: cell.row.original.id }}
              className="font-label hover:underline"
            >
              {cell.getValue()}
            </Link>
          ),
        }),
        monoColumn(columns, "id", t("roles.columns.id"), (role) => role.id),
        columns.accessor("builtIn", {
          header: () => t("roles.columns.kind"),
          cell: (cell) =>
            cell.getValue() ? (
              <Badge tone="neutral" icon={Lock}>
                {t("roles.builtIn")}
              </Badge>
            ) : (
              t("roles.custom")
            ),
        }),
        columns.accessor((role) => role.permissions.length, {
          id: "permissions",
          header: () => t("roles.columns.permissions"),
          cell: (cell) => t("access.count", { count: cell.getValue() }),
        }),
      ]),
    [t],
  );

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("nav.roles")}
        description={t("roles.description")}
        actions={
          isOwner ? (
            <Button
              kind="primary"
              icon={Plus}
              onClick={() => setCreating(true)}
            >
              {t("roles.create.title")}
            </Button>
          ) : undefined
        }
      />
      <section className="rounded-card border border-border-subtle bg-surface py-1 shadow-card">
        <DataTable
          label={t("nav.roles")}
          data={roles.data?.data ?? []}
          failure={loadFailure(roles)}
          columns={roleColumns}
          getRowId={(role) => role.id}
          actions={(role) => ({
            menu: [
              [
                {
                  label: t("roles.open"),
                  icon: ArrowUpRight,
                  onSelect: () =>
                    navigate({
                      to: "/roles/$roleId",
                      params: { roleId: role.id },
                    }),
                },
              ],
            ],
          })}
          loading={roles.isPending}
          empty={
            <EmptyState
              icon={KeyRound}
              title={t("roles.empty")}
              description={t("roles.emptyDescription")}
            />
          }
        />
      </section>
      <NameIdDialog
        open={creating}
        onOpenChange={setCreating}
        title={t("roles.create.title")}
        description={t("roles.create.description")}
        submitLabel={t("roles.create.submit")}
        name={{
          label: t("roles.form.name"),
          placeholder: t("roles.form.namePlaceholder"),
        }}
        id={{
          label: t("roles.form.id"),
          hint: t("roles.form.idHint"),
          placeholder: "support",
          schema: RoleCreateBody.shape.id,
        }}
        create={(id, name) =>
          create.mutateAsync({ data: { id, name, permissions: [] } })
        }
        done={(name) => t("roles.create.done", { name })}
        onCreated={(id) =>
          void navigate({ to: "/roles/$roleId", params: { roleId: id } })
        }
      />
    </div>
  );
}
