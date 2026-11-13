import type { UpdateUserRequest, UserView } from "@ogcloud/api-client";
import {
  getUserGetQueryKey,
  getUserListQueryKey,
  useUserGet,
  useUserUpdate,
} from "@ogcloud/api-client/react";
import { UserUpdateBody } from "@ogcloud/api-client/zod";
import {
  Badge,
  Button,
  Card,
  Loading,
  Note,
  PageHeader,
  Value,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { KeyRound, Lock, LogOut, UserRound, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { DetailFallback } from "../../detail-fallback.tsx";
import { DetailRow } from "../../details.tsx";
import { EditorForm, useDetailSave } from "../../editing.tsx";
import { contractSchema, useSettingsForm } from "../../form.tsx";
import { IdentityCard } from "../../identity-card.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { isOwner, useIsOwner, useMe } from "../../permissions.ts";
import { placeholderUser } from "../../placeholders.ts";
import { invalidatePaths } from "../../queries.ts";
import { usePrivacyActions } from "../privacy/privacy-actions.tsx";
import { useRoleNames } from "./role-names.ts";
import { groupsOf, useUserActions } from "./user-actions.tsx";
import { UserStatusBadge } from "./user-status.tsx";

type UserSettings = Omit<UpdateUserRequest, "version">;

const SettingsForm = contractSchema<{ settings: UserSettings }>(
  z.object({
    settings: UserUpdateBody.pick({
      username: true,
      email: true,
      roleId: true,
      enabled: true,
    }),
  }),
);

/** One dashboard user: name, email, role and access edited in place, with their account actions. */
export function UserPage() {
  const { t } = useTranslation("web");
  const { userId } = useParams({ from: "/app/users/$userId" });
  const user = useUserGet(userId);

  if (user.data === undefined && user.error !== null) {
    return (
      <DetailFallback
        error={user.error}
        icon={Users}
        goneTitle={t("users.detail.gone")}
        goneDescription={t("users.detail.goneDescription")}
        failedTitle={t("users.detail.failed")}
        back={
          <Link to="/users" className="font-label text-accent">
            {t("users.detail.back")}
          </Link>
        }
      />
    );
  }
  const view = user.data?.data;
  // A new key when the data arrives, so the form starts from it rather than from the placeholder.
  return (
    <Loading when={view === undefined}>
      <UserEditor
        key={view === undefined ? "loading" : `loaded:${view.id}`}
        user={view ?? placeholderUser}
      />
    </Loading>
  );
}

function UserEditor({ user }: { user: UserView }) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const queries = useQueryClient();
  const me = useMe();
  const callerIsOwner = useIsOwner();
  const roles = useRoleNames();
  const leave = () => void navigate({ to: "/users" });
  const actions = useUserActions(leave);
  const privacy = usePrivacyActions(leave);
  const canWrite = actions.canManage(user);
  const update = useUserUpdate({
    mutation: {
      onSettled: () => invalidatePaths(queries, [getUserListQueryKey()[0]]),
    },
  });
  const outcome = useDetailSave(getUserGetQueryKey(user.id));

  const { form, changes, saving } = useSettingsForm({
    view: user,
    settingsOf: ({ username, email, roleId, enabled }) => ({
      username,
      email,
      roleId,
      enabled,
    }),
    schema: SettingsForm,
    save: (settings, base) =>
      update.mutateAsync({
        id: base.id,
        data: { ...settings, version: base.version },
      }),
    ...outcome,
  });
  const items = actions.itemsFor(user);
  const subject = {
    kind: "user" as const,
    id: user.id,
    name: user.username,
    owner: isOwner(user),
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={<Value>{user.username}</Value>}
        badges={
          <>
            <UserStatusBadge enabled={user.enabled} />
            {user.id === me.id ? (
              <Badge tone="highlight" icon={UserRound}>
                {t("users.you")}
              </Badge>
            ) : null}
          </>
        }
        actions={
          <>
            <MoreMenu
              groups={groupsOf([
                [items.unlink],
                privacy.itemsFor(subject),
                [items.delete],
              ])}
            />
            {canWrite ? (
              <>
                <Button
                  icon={LogOut}
                  onClick={() => actions.open("signOut", user)}
                >
                  {t("users.actions.signOut")}
                </Button>
                <Button
                  icon={KeyRound}
                  onClick={() => actions.open("reset", user)}
                >
                  {t("users.actions.reset")}
                </Button>
              </>
            ) : null}
          </>
        }
      />
      <EditorForm
        form={form}
        canWrite={canWrite}
        changes={changes}
        saving={saving}
        aside={
          <IdentityCard
            label={t("users.detail.id")}
            id={user.id}
            createdAt={user.createdAt}
          >
            <DetailRow label={t("users.columns.minecraft")}>
              {user.linkedPlayerName == null ? (
                <span className="text-text-3">{t("users.notLinked")}</span>
              ) : (
                <span className="font-mono text-mono">
                  {user.linkedPlayerName}
                </span>
              )}
            </DetailRow>
          </IdentityCard>
        }
      >
        {canWrite ? null : (
          <Note tone="info" icon={Lock}>
            {isOwner(user)
              ? t("users.detail.ownerOnly")
              : t("users.detail.readOnly")}
          </Note>
        )}
        <Card title={t("users.detail.profile")}>
          <div className="flex flex-col gap-3">
            <form.AppField name="settings.username">
              {(field) => (
                <field.TextField
                  label={t("users.form.username")}
                  hint={t("users.form.usernameHint")}
                />
              )}
            </form.AppField>
            <form.AppField name="settings.email">
              {(field) => <field.TextField label={t("users.form.email")} />}
            </form.AppField>
          </div>
        </Card>
        <Card title={t("users.detail.access")}>
          <div className="flex flex-col gap-3">
            <form.AppField name="settings.roleId">
              {(field) => (
                <field.SelectField
                  label={t("users.form.role")}
                  hint={
                    callerIsOwner
                      ? t("users.form.roleHint")
                      : t("users.form.roleOwnerOnly")
                  }
                  options={
                    roles.options.length > 0
                      ? roles.options
                      : [{ value: user.roleId, label: user.roleId }]
                  }
                  disabled={!callerIsOwner}
                />
              )}
            </form.AppField>
            <form.AppField name="settings.enabled">
              {(field) => (
                <field.SwitchField
                  label={t("users.form.enabled")}
                  description={t("users.form.enabledHint")}
                />
              )}
            </form.AppField>
          </div>
        </Card>
      </EditorForm>
      {actions.dialogs}
      {privacy.dialogs}
    </div>
  );
}
