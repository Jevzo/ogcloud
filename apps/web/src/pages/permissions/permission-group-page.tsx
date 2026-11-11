import type {
  PermissionGroupSettings,
  PermissionGroupView,
} from "@ogcloud/api-client";
import {
  getPermissionGroupGetQueryKey,
  getPermissionGroupListQueryKey,
  usePermissionGroupDelete,
  usePermissionGroupGet,
  usePermissionGroupUpdate,
} from "@ogcloud/api-client/react";
import { PermissionGroupUpdateBody } from "@ogcloud/api-client/zod";
import { Badge, Card, PageHeader } from "@ogcloud/ui";
import { useStore } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { Shield, Star } from "lucide-react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { useDeleteAction } from "../../delete-action.tsx";
import { DetailFallback } from "../../detail-fallback.tsx";
import { EditorForm, useDetailSave } from "../../editing.tsx";
import { contractSchema, useViewSettingsForm } from "../../form.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { useCan } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";
import { useTextValidator } from "../../texts.ts";
import { PermissionsEditor } from "./permissions-editor.tsx";
import { RankLook } from "./rank-look.tsx";

const SettingsForm = contractSchema<{ settings: PermissionGroupSettings }>(
  z.object({ settings: PermissionGroupUpdateBody.shape.settings }),
);

/** One permission group's page: its look and nodes edited in place, previewed as the game shows them. */
export function PermissionGroupPage() {
  const { t } = useTranslation("web");
  const { permissionGroupId } = useParams({
    from: "/app/permissions/$permissionGroupId",
  });
  const group = usePermissionGroupGet(permissionGroupId);

  if (group.data === undefined) {
    return (
      <DetailFallback
        error={group.error}
        icon={Shield}
        goneTitle={t("permissions.detail.gone")}
        goneDescription={t("permissions.detail.goneDescription")}
        failedTitle={t("permissions.detail.failed")}
        back={
          <Link to="/permissions" className="font-label text-accent-text">
            {t("permissions.detail.back")}
          </Link>
        }
      />
    );
  }
  return (
    <PermissionGroupEditor key={group.data.data.id} view={group.data.data} />
  );
}

function PermissionGroupEditor({ view }: { view: PermissionGroupView }) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const client = useQueryClient();
  const canWrite = useCan("permissions:write");
  const update = usePermissionGroupUpdate();
  const remove = usePermissionGroupDelete({
    mutation: {
      onSettled: () =>
        invalidatePaths(client, [getPermissionGroupListQueryKey()[0]]),
    },
  });
  const deletion = useDeleteAction({
    label: t("permissions.detail.delete"),
    title: t("permissions.detail.deleteTitle", { name: view.settings.name }),
    description: t("permissions.detail.deleteDescription"),
    typedName: view.id,
    done: t("permissions.detail.deleted", { name: view.settings.name }),
    remove: () => remove.mutateAsync({ id: view.id }),
    onDeleted: () => void navigate({ to: "/permissions" }),
  });
  const outcome = useDetailSave(getPermissionGroupGetQueryKey(view.id));
  const validators = {
    chatPrefix: useTextValidator("CHAT_PREFIX"),
    chatSuffix: useTextValidator("CHAT_SUFFIX"),
    nameFormat: useTextValidator("NAME_FORMAT"),
    tabPrefix: useTextValidator("TAB_PREFIX"),
  };

  const { form, changes, saving } = useViewSettingsForm({
    view,
    schema: SettingsForm,
    save: (settings, base) =>
      update.mutateAsync({
        id: base.id,
        data: { settings, version: base.version },
      }),
    onSaved: (answer) => {
      void invalidatePaths(client, [getPermissionGroupListQueryKey()[0]]);
      outcome.onSaved(answer);
    },
    onFailed: outcome.onFailed,
  });
  const display = useStore(
    form.store,
    (state) => state.values.settings.display,
  );

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {view.settings.name}
            {view.settings.defaultGroup ? (
              <Badge tone="accent" icon={Star}>
                {t("permissions.default")}
              </Badge>
            ) : null}
          </span>
        }
        description={t("permissions.detail.description", { id: view.id })}
        actions={
          canWrite && !view.settings.defaultGroup ? (
            <MoreMenu groups={[[deletion.item]]} />
          ) : undefined
        }
      />
      <EditorForm
        form={form}
        canWrite={canWrite}
        changes={changes}
        saving={saving}
        aside={
          <Card title={t("permissions.look.title")}>
            <RankLook display={display} />
          </Card>
        }
      >
        <Card title={t("permissions.form.basics")}>
          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-[minmax(0,1fr)_160px] gap-4">
              <form.AppField name="settings.name">
                {(field) => (
                  <field.TextField label={t("permissions.form.name")} />
                )}
              </form.AppField>
              <form.AppField name="settings.weight">
                {(field) => (
                  <field.NumberField
                    label={t("permissions.form.weight")}
                    hint={t("permissions.form.weightHint")}
                  />
                )}
              </form.AppField>
            </div>
            <form.AppField name="settings.defaultGroup">
              {(field) => (
                <field.SwitchField
                  label={t("permissions.form.default")}
                  description={t("permissions.form.defaultHint")}
                  disabled={view.settings.defaultGroup}
                />
              )}
            </form.AppField>
          </div>
        </Card>
        <Card title={t("permissions.form.look")}>
          <div className="flex flex-col gap-3">
            <form.AppField
              name="settings.display.chatPrefix"
              validators={validators.chatPrefix}
            >
              {(field) => (
                <field.TagTextFormField
                  field="CHAT_PREFIX"
                  label={t("permissions.form.chatPrefix")}
                />
              )}
            </form.AppField>
            <form.AppField
              name="settings.display.nameFormat"
              validators={validators.nameFormat}
            >
              {(field) => (
                <field.TagTextFormField
                  field="NAME_FORMAT"
                  label={t("permissions.form.nameFormat")}
                  hint={t("permissions.form.nameFormatHint")}
                />
              )}
            </form.AppField>
            <form.AppField
              name="settings.display.chatSuffix"
              validators={validators.chatSuffix}
            >
              {(field) => (
                <field.TagTextFormField
                  field="CHAT_SUFFIX"
                  label={t("permissions.form.chatSuffix")}
                />
              )}
            </form.AppField>
            <form.AppField
              name="settings.display.tabPrefix"
              validators={validators.tabPrefix}
            >
              {(field) => (
                <field.TagTextFormField
                  field="TAB_PREFIX"
                  label={t("permissions.form.tabPrefix")}
                  hint={t("permissions.form.tabPrefixHint")}
                />
              )}
            </form.AppField>
          </div>
        </Card>
        <Card title={t("permissions.form.nodes")}>
          <form.Field name="settings.permissions">
            {(field) => (
              <PermissionsEditor
                value={field.state.value}
                onChange={field.handleChange}
                disabled={!canWrite}
              />
            )}
          </form.Field>
        </Card>
      </EditorForm>
      {deletion.dialog}
    </div>
  );
}
