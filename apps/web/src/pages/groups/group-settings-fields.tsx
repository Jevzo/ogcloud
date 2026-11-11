import type {
  GroupSettings,
  GroupViewType,
  ImageView,
  RuntimeProfileView,
} from "@ogcloud/api-client";
import { Card, Field, Input } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";
import { withFieldGroup } from "../../form.tsx";

const PERCENT = 100;

/** Every editable setting of a group, in cards; the create page and a group's page share them (D-0210). */
export const GroupSettingsFields = withFieldGroup({
  defaultValues: {} as GroupSettings,
  props: {
    type: "DYNAMIC" as GroupViewType,
    profiles: [] as readonly RuntimeProfileView[],
  },
  render: function GroupSettingsFieldsRender({ group, type, profiles }) {
    const { t } = useTranslation("web");
    const role = type === "PROXY" ? "PROXY" : "BACKEND";
    const seconds = t("groups.form.seconds");
    return (
      <div className="flex flex-col gap-4">
        <Card title={t("groups.form.runtime")}>
          <group.AppField name="profileId">
            {(field) => (
              <field.SelectField
                label={t("groups.form.profile")}
                hint={t("groups.form.profileHint")}
                placeholder={t("groups.form.pickProfile")}
                options={profiles
                  .filter((profile) => profile.role === role)
                  .map((profile) => ({
                    value: profile.id,
                    label: profile.id,
                    hint: t("groups.form.profileJava", {
                      java: profile.javaVersion,
                    }),
                  }))}
              />
            )}
          </group.AppField>
        </Card>
        <Card title={t("groups.form.scaling")}>
          <div className="grid grid-cols-2 gap-x-4 gap-y-3">
            <group.AppField name="scaling.minOnline">
              {(field) => (
                <field.NumberField
                  label={t("groups.form.minOnline")}
                  hint={t("groups.form.minOnlineHint")}
                />
              )}
            </group.AppField>
            <group.AppField name="scaling.maxInstances">
              {(field) => (
                <field.NumberField
                  label={t("groups.form.maxInstances")}
                  hint={t("groups.form.maxInstancesHint")}
                />
              )}
            </group.AppField>
            <group.AppField name="scaling.playersPerServer">
              {(field) => (
                <field.NumberField
                  label={t("groups.form.playersPerServer")}
                  hint={t("groups.form.playersPerServerHint")}
                />
              )}
            </group.AppField>
            <group.AppField name="scaling.cooldownSeconds">
              {(field) => (
                <field.NumberField
                  label={t("groups.form.cooldown")}
                  hint={t("groups.form.cooldownHint")}
                  suffix={seconds}
                />
              )}
            </group.AppField>
            <group.AppField name="scaling.scaleUpThreshold">
              {(field) => (
                <field.NumberField
                  label={t("groups.form.scaleUp")}
                  hint={t("groups.form.scaleUpHint")}
                  suffix="%"
                  scale={PERCENT}
                />
              )}
            </group.AppField>
            <group.AppField name="scaling.scaleDownThreshold">
              {(field) => (
                <field.NumberField
                  label={t("groups.form.scaleDown")}
                  hint={t("groups.form.scaleDownHint")}
                  suffix="%"
                  scale={PERCENT}
                />
              )}
            </group.AppField>
          </div>
        </Card>
        <Card title={t("groups.form.resources")}>
          <div className="grid grid-cols-2 gap-x-4 gap-y-3">
            <group.AppField name="resources.memoryRequest">
              {(field) => (
                <field.TextField
                  mono
                  label={t("groups.form.memoryRequest")}
                  hint={t("groups.form.memoryHint")}
                />
              )}
            </group.AppField>
            <group.AppField name="resources.memoryLimit">
              {(field) => (
                <field.TextField
                  mono
                  label={t("groups.form.memoryLimit")}
                  hint={t("groups.form.memoryLimitHint")}
                />
              )}
            </group.AppField>
            <group.AppField name="resources.cpuRequest">
              {(field) => (
                <field.TextField
                  mono
                  label={t("groups.form.cpuRequest")}
                  hint={t("groups.form.cpuHint")}
                />
              )}
            </group.AppField>
            <group.AppField name="resources.cpuLimit">
              {(field) => (
                <field.TextField
                  optional
                  mono
                  label={t("groups.form.cpuLimit")}
                  hint={t("groups.form.cpuLimitHint")}
                />
              )}
            </group.AppField>
          </div>
        </Card>
        <Card title={t("groups.form.startup")}>
          <div className="flex flex-col gap-3">
            <group.AppField name="drainTimeoutSeconds">
              {(field) => (
                <field.NumberField
                  label={t("groups.form.drainTimeout")}
                  hint={t("groups.form.drainTimeoutHint")}
                  suffix={seconds}
                />
              )}
            </group.AppField>
            <group.AppField name="jvmFlags">
              {(field) => (
                <field.TextField
                  mono
                  label={t("groups.form.jvmFlags")}
                  hint={t("groups.form.jvmFlagsHint")}
                  placeholder="-XX:+UseZGC"
                />
              )}
            </group.AppField>
            {type === "STATIC" ? (
              <group.AppField name="storageSize">
                {(field) => (
                  <field.TextField
                    optional
                    mono
                    label={t("groups.form.storage")}
                    hint={t("groups.form.storageHint")}
                  />
                )}
              </group.AppField>
            ) : null}
          </div>
        </Card>
        <Card title={t("groups.form.image")}>
          <group.Field name="image">
            {(field) => (
              <ImageOverride
                value={field.state.value}
                onChange={field.handleChange}
              />
            )}
          </group.Field>
        </Card>
      </div>
    );
  },
});

/** A custom image for the group's servers: an empty image name means OgCloud's own image. */
function ImageOverride({
  value,
  onChange,
}: {
  value: ImageView | null | undefined;
  onChange: (image: ImageView | null) => void;
}) {
  const { t } = useTranslation("web");
  return (
    <div className="flex flex-col gap-3">
      <Field
        label={t("groups.form.imageName")}
        hint={t("groups.form.imageHint")}
      >
        {(control) => (
          <Input
            {...control}
            mono
            value={value?.image ?? ""}
            placeholder={t("groups.form.imageDefault")}
            onChange={(event) =>
              onChange(
                event.target.value === ""
                  ? null
                  : {
                      image: event.target.value,
                      pullSecretRef: value?.pullSecretRef ?? null,
                    },
              )
            }
          />
        )}
      </Field>
      {value == null ? null : (
        <Field
          label={t("groups.form.pullSecret")}
          hint={t("groups.form.pullSecretHint")}
        >
          {(control) => (
            <Input
              {...control}
              mono
              value={value.pullSecretRef ?? ""}
              onChange={(event) =>
                onChange({
                  image: value.image,
                  pullSecretRef:
                    event.target.value === "" ? null : event.target.value,
                })
              }
            />
          )}
        </Field>
      )}
    </div>
  );
}
