import type { RoleView, UpdateRoleRequest } from "@ogcloud/api-client";
import {
  getRoleListQueryKey,
  useRoleDelete,
  useRoleList,
  useRoleUpdate,
  useUserList,
} from "@ogcloud/api-client/react";
import { RoleUpdateBody } from "@ogcloud/api-client/zod";
import { Badge, Card, Loading, Note, PageHeader, Value } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { KeyRound, Lock } from "lucide-react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { useDeleteAction } from "../../delete-action.tsx";
import { DetailFallback } from "../../detail-fallback.tsx";
import { EditorForm, useListSave } from "../../editing.tsx";
import { contractSchema, useSettingsForm } from "../../form.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { useCan, useIsOwner } from "../../permissions.ts";
import { placeholderRole } from "../../placeholders.ts";
import { invalidatePaths } from "../../queries.ts";
import { PermissionsCard } from "./permission-picker.tsx";

type RoleSettings = Pick<UpdateRoleRequest, "name" | "permissions">;

const SettingsForm = contractSchema<{ settings: RoleSettings }>(
  z.object({
    settings: RoleUpdateBody.pick({ name: true, permissions: true }),
  }),
);
// Enough for the members panel of a small network; the users page lists everyone.
const MEMBERS_LIMIT = 200;

/** One role: its name and permissions, edited in place by owners (D-0094); built-in roles are fixed (D-0089). */
export function RolePage() {
  const { t } = useTranslation("web");
  const { roleId } = useParams({ from: "/app/roles/$roleId" });
  const roles = useRoleList();
  const role = roles.data?.data.find((candidate) => candidate.id === roleId);

  if (role === undefined && !roles.isPending) {
    return (
      <DetailFallback
        error={roles.error}
        missing={roles.isSuccess}
        icon={KeyRound}
        goneTitle={t("roles.detail.gone")}
        goneDescription={t("roles.detail.goneDescription")}
        failedTitle={t("roles.detail.failed")}
        back={
          <Link to="/roles" className="font-label text-accent-text">
            {t("roles.detail.back")}
          </Link>
        }
      />
    );
  }
  return (
    <Loading when={role === undefined}>
      <RoleEditor
        key={role === undefined ? "loading" : `loaded:${role.id}`}
        role={role ?? placeholderRole}
      />
    </Loading>
  );
}

function RoleEditor({ role }: { role: RoleView }) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const client = useQueryClient();
  const isOwner = useIsOwner();
  const canWrite = isOwner && !role.builtIn;
  const update = useRoleUpdate();
  const remove = useRoleDelete({
    mutation: {
      onSettled: () => invalidatePaths(client, [getRoleListQueryKey()[0]]),
    },
  });
  const deletion = useDeleteAction({
    label: t("roles.detail.delete"),
    title: t("roles.detail.deleteTitle", { name: role.name }),
    description: t("roles.detail.deleteDescription"),
    typedName: role.id,
    done: t("roles.detail.deleted", { name: role.name }),
    remove: () => remove.mutateAsync({ id: role.id }),
    onDeleted: () => void navigate({ to: "/roles" }),
  });
  const outcome = useListSave(getRoleListQueryKey()[0]);

  const { form, changes, saving } = useSettingsForm({
    view: role,
    settingsOf: ({ name, permissions }) => ({ name, permissions }),
    schema: SettingsForm,
    save: (settings, base) =>
      update.mutateAsync({
        id: base.id,
        data: { ...settings, version: base.version },
      }),
    ...outcome,
  });

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={<Value>{role.name}</Value>}
        badges={
          role.builtIn ? (
            <Badge tone="neutral" icon={Lock}>
              {t("roles.builtIn")}
            </Badge>
          ) : null
        }
        actions={canWrite ? <MoreMenu groups={[[deletion.item]]} /> : undefined}
      />
      <EditorForm
        form={form}
        canWrite={canWrite}
        changes={changes}
        saving={saving}
        aside={<MembersCard roleId={role.id} />}
      >
        {role.builtIn ? (
          <Note tone="info" icon={Lock}>
            {t("roles.detail.builtInNote")}
          </Note>
        ) : isOwner ? null : (
          <Note tone="info" icon={Lock}>
            {t("roles.detail.ownerOnly")}
          </Note>
        )}
        <Card title={t("roles.form.basics")}>
          <form.AppField name="settings.name">
            {(field) => <field.TextField label={t("roles.form.name")} />}
          </form.AppField>
        </Card>
        <form.Field name="settings.permissions">
          {(field) => (
            <PermissionsCard
              value={field.state.value}
              onChange={field.handleChange}
              disabled={!canWrite}
            />
          )}
        </form.Field>
      </EditorForm>
      {deletion.dialog}
    </div>
  );
}

/** Who holds the role, for readers of users; the users page has everyone. */
function MembersCard({ roleId }: { roleId: string }) {
  const { t } = useTranslation("web");
  const canRead = useCan("users:read");
  const users = useUserList(
    { limit: MEMBERS_LIMIT },
    { query: { enabled: canRead } },
  );
  if (!canRead) return null;
  const members = (users.data?.data.items ?? []).filter(
    (user) => user.roleId === roleId,
  );
  return (
    <Card title={t("roles.detail.members", { count: members.length })}>
      {members.length === 0 ? (
        <p className="text-text-3">{t("roles.detail.noMembers")}</p>
      ) : (
        <ul className="flex flex-col gap-1.5">
          {members.map((user) => (
            <li
              key={user.id}
              className="flex items-center justify-between gap-3"
            >
              <Link
                to="/users/$userId"
                params={{ userId: user.id }}
                className="font-label hover:underline"
              >
                {user.username}
              </Link>
              <span className="truncate text-meta text-text-3">
                {user.email}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
