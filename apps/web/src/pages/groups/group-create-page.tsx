import type { CreateGroupRequest, GroupViewType } from "@ogcloud/api-client";
import {
  getGroupListQueryKey,
  getOverviewGetQueryKey,
  useGroupCreate,
  useRuntimeList,
} from "@ogcloud/api-client/react";
import { GroupCreateBody } from "@ogcloud/api-client/zod";
import { Card, Field, PageHeader, Segmented, useToast } from "@ogcloud/ui";
import { useStore } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import {
  CreateActions,
  EditorPage,
  editorAside,
  editorColumns,
} from "../../editing.tsx";
import { useFailureToast } from "../../errors.ts";
import { contractSchema, useAppForm } from "../../form.tsx";
import { invalidatePaths } from "../../queries.ts";
import { CapacitySummary } from "./capacity-summary.tsx";
import { newGroupSettings } from "./group-defaults.ts";
import { GroupSettingsFields } from "./group-settings-fields.tsx";

const TYPES: readonly GroupViewType[] = ["DYNAMIC", "STATIC", "PROXY"];

/** A new group: its name and type, then every setting, starting from the type's defaults (D-0210). */
export function GroupCreatePage() {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const navigate = useNavigate();
  const client = useQueryClient();
  const runtime = useRuntimeList();
  const profiles = runtime.data?.data ?? [];
  const create = useGroupCreate({
    mutation: {
      onSettled: () =>
        invalidatePaths(client, [
          getGroupListQueryKey()[0],
          getOverviewGetQueryKey()[0],
        ]),
    },
  });

  const form = useAppForm({
    defaultValues: {
      id: "",
      type: "DYNAMIC" as GroupViewType,
      settings: newGroupSettings("DYNAMIC", []),
    },
    validators: {
      onChange: contractSchema<CreateGroupRequest>(GroupCreateBody),
    },
    onSubmit: ({ value }) =>
      create.mutateAsync({ data: value }).then(() => {
        toast({ message: t("groups.create.done", { id: value.id }) });
        void navigate({
          to: "/groups/$groupId",
          params: { groupId: value.id },
        });
      }, failed),
  });
  const submitting = useStore(form.store, (state) => state.isSubmitting);

  // The runtime list arrives after the form; pick the type's profile once it does.
  useEffect(() => {
    if (form.getFieldValue("settings.profileId") !== "") return;
    const type = form.getFieldValue("type");
    form.setFieldValue(
      "settings.profileId",
      newGroupSettings(type, profiles).profileId,
    );
  }, [form, profiles]);

  return (
    <EditorPage>
      <PageHeader title={t("groups.create.title")} />
      <form
        className={editorColumns}
        onSubmit={(event) => {
          event.preventDefault();
          void form.handleSubmit();
        }}
      >
        <div className="flex min-w-0 flex-col gap-6">
          <Card title={t("groups.form.basics")}>
            <div className="flex flex-col gap-3">
              <form.AppField name="id">
                {(field) => (
                  <field.TextField
                    mono
                    label={t("groups.form.id")}
                    hint={t("groups.form.idHint")}
                    placeholder="bedwars"
                  />
                )}
              </form.AppField>
              <form.Field name="type">
                {(field) => (
                  <Field
                    label={t("groups.form.type")}
                    hint={t(`groups.form.typeHint.${field.state.value}`)}
                  >
                    {() => (
                      <Segmented
                        label={t("groups.form.type")}
                        options={TYPES.map((type) => ({
                          value: type,
                          label: t(`groups.type.${type}`),
                        }))}
                        value={field.state.value}
                        onValueChange={(type) => {
                          field.handleChange(type);
                          form.setFieldValue(
                            "settings",
                            newGroupSettings(type, profiles),
                          );
                        }}
                      />
                    )}
                  </Field>
                )}
              </form.Field>
            </div>
          </Card>
          <form.Subscribe selector={(state) => state.values.type}>
            {(type) => (
              <GroupSettingsFields
                form={form}
                fields="settings"
                type={type}
                profiles={profiles}
              />
            )}
          </form.Subscribe>
          <CreateActions
            onCancel={() => void navigate({ to: "/groups" })}
            submitLabel={t("groups.create.submit")}
            submitting={submitting}
          />
        </div>
        <div className={editorAside}>
          <form.Subscribe selector={(state) => state.values.settings}>
            {(settings) => <CapacitySummary settings={settings} />}
          </form.Subscribe>
        </div>
      </form>
    </EditorPage>
  );
}
