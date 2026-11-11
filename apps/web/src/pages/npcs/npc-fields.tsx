import type {
  NpcSettings,
  SkinView,
  TransferActionView,
  TransferActionViewRoutingStrategy,
} from "@ogcloud/api-client";
import { Card, Field, Input, Select } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";
import { withFieldGroup } from "../../form.tsx";
import { useTextValidator } from "../../texts.ts";
import { ROUTING_STRATEGIES } from "../network/routing.ts";

// No group id can be "*" (they are DNS labels), so it stands for "no transfer" and "the network's strategy".
const NONE = "*";

/** Every setting of an NPC, in cards; the create page and an NPC's page share them (D-0210). */
export const NpcFields = withFieldGroup({
  defaultValues: {} as NpcSettings,
  props: { groupIds: [] as readonly string[] },
  render: function NpcFieldsRender({ group, groupIds }) {
    const { t } = useTranslation("web");
    const title = useTextValidator("NPC_TITLE");
    const subtitle = useTextValidator("NPC_SUBTITLE");
    const groupOptions = groupIds.map((id) => ({ value: id, label: id }));
    return (
      <div className="flex flex-col gap-4">
        <Card title={t("npcs.form.look")}>
          <div className="flex flex-col gap-3">
            <group.AppField name="groupId">
              {(field) => (
                <field.SelectField
                  label={t("npcs.form.group")}
                  hint={t("npcs.form.groupHint")}
                  options={groupOptions}
                  placeholder={t("servers.request.pickGroup")}
                />
              )}
            </group.AppField>
            <group.AppField name="title" validators={title}>
              {(field) => (
                <field.TagTextFormField
                  field="NPC_TITLE"
                  label={t("npcs.form.title")}
                />
              )}
            </group.AppField>
            <group.AppField name="subtitle" validators={subtitle}>
              {(field) => (
                <field.TagTextFormField
                  field="NPC_SUBTITLE"
                  label={t("npcs.form.subtitle")}
                  hint={t("npcs.form.subtitleHint")}
                />
              )}
            </group.AppField>
            <group.AppField name="lookAtPlayers">
              {(field) => (
                <field.SwitchField
                  label={t("npcs.form.lookAt")}
                  description={t("npcs.form.lookAtHint")}
                />
              )}
            </group.AppField>
            <group.Field name="skin">
              {(field) => (
                <SkinFields
                  value={field.state.value}
                  onChange={field.handleChange}
                />
              )}
            </group.Field>
          </div>
        </Card>
        <Card title={t("npcs.form.location")}>
          <div className="grid grid-cols-3 gap-x-4 gap-y-3">
            <div className="col-span-3">
              <group.AppField name="location.world">
                {(field) => (
                  <field.TextField mono label={t("npcs.form.world")} />
                )}
              </group.AppField>
            </div>
            {(["x", "y", "z", "yaw", "pitch"] as const).map((axis) => (
              <group.AppField key={axis} name={`location.${axis}`}>
                {(field) => (
                  <field.NumberField label={t(`npcs.form.${axis}`)} />
                )}
              </group.AppField>
            ))}
          </div>
        </Card>
        <Card title={t("npcs.form.clicks")}>
          <div className="flex flex-col gap-3">
            <p className="text-small text-text-3">
              {t("npcs.form.clicksHint")}
            </p>
            {(["rightClick", "leftClick"] as const).map((click) => (
              <group.Field key={click} name={click}>
                {(field) => (
                  <ClickFields
                    label={t(`npcs.form.${click}`)}
                    groupIds={groupIds}
                    value={field.state.value}
                    onChange={field.handleChange}
                  />
                )}
              </group.Field>
            ))}
          </div>
        </Card>
      </div>
    );
  },
});

/** A custom skin as the game signs it; an empty texture keeps the default model. */
function SkinFields({
  value,
  onChange,
}: {
  value: SkinView | null | undefined;
  onChange: (skin: SkinView | null) => void;
}) {
  const { t } = useTranslation("web");
  return (
    <div className="grid grid-cols-2 gap-4">
      <Field label={t("npcs.form.skinTexture")} hint={t("npcs.form.skinHint")}>
        {(control) => (
          <Input
            {...control}
            mono
            value={value?.texture ?? ""}
            onChange={(event) =>
              onChange(
                event.target.value === ""
                  ? null
                  : {
                      texture: event.target.value,
                      signature: value?.signature ?? "",
                    },
              )
            }
          />
        )}
      </Field>
      <Field label={t("npcs.form.skinSignature")}>
        {(control) => (
          <Input
            {...control}
            mono
            disabled={value == null}
            value={value?.signature ?? ""}
            onChange={(event) =>
              value == null
                ? undefined
                : onChange({
                    texture: value.texture,
                    signature: event.target.value,
                  })
            }
          />
        )}
      </Field>
    </div>
  );
}

/** What one click does: nothing, or move the player to a group by a strategy. */
function ClickFields({
  label,
  groupIds,
  value,
  onChange,
}: {
  label: string;
  groupIds: readonly string[];
  value: TransferActionView | null | undefined;
  onChange: (action: TransferActionView | null) => void;
}) {
  const { t } = useTranslation("web");
  return (
    <div className="grid grid-cols-2 gap-4">
      <Field label={label}>
        {(control) => (
          <Select
            {...control}
            options={[
              { value: NONE, label: t("npcs.form.nothing") },
              ...groupIds.map((id) => ({
                value: id,
                label: t("npcs.form.moveTo", { group: id }),
              })),
            ]}
            value={value?.groupId ?? NONE}
            onValueChange={(groupId) =>
              onChange(
                groupId === NONE
                  ? null
                  : value?.routingStrategy === undefined
                    ? { groupId }
                    : { groupId, routingStrategy: value.routingStrategy },
              )
            }
          />
        )}
      </Field>
      <Field label={t("npcs.form.strategy")}>
        {(control) => (
          <Select
            {...control}
            disabled={value == null}
            options={[
              { value: NONE, label: t("npcs.form.networkStrategy") },
              ...ROUTING_STRATEGIES.map((strategy) => ({
                value: strategy,
                label: t(`network.routing.names.${strategy}`),
              })),
            ]}
            value={value?.routingStrategy ?? NONE}
            onValueChange={(strategy) =>
              value == null
                ? undefined
                : onChange(
                    strategy === NONE
                      ? { groupId: value.groupId }
                      : {
                          groupId: value.groupId,
                          routingStrategy:
                            strategy as TransferActionViewRoutingStrategy,
                        },
                  )
            }
          />
        )}
      </Field>
    </div>
  );
}
