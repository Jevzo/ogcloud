import type { GroupSettings } from "@ogcloud/api-client";
import { Card } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";
import { DetailRow, Details } from "../../details.tsx";
import { useFormat } from "../../format.ts";

const UNITS: Record<string, number> = {
  "": 1,
  K: 1e3,
  M: 1e6,
  G: 1e9,
  T: 1e12,
  Ki: 2 ** 10,
  Mi: 2 ** 20,
  Gi: 2 ** 30,
  Ti: 2 ** 40,
};
const GIB = 2 ** 30;

/** A Kubernetes memory quantity such as "2Gi" in GiB, or null while it is not one. */
export function gibOf(quantity: string): number | null {
  const match = /^(\d+(?:\.\d+)?)(Ki|Mi|Gi|Ti|K|M|G|T)?$/.exec(quantity.trim());
  if (match === null) return null;
  return (Number(match[1]) * (UNITS[match[2] ?? ""] ?? 1)) / GIB;
}

/** What a new group could hold at its most, from the form as it is filled in (D-0231). */
export function CapacitySummary({ settings }: { settings: GroupSettings }) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const { maxInstances, playersPerServer } = settings.scaling;
  const gib = gibOf(settings.resources.memoryLimit);
  const amount = (value: number) => format.number(Math.round(value * 10) / 10);
  return (
    <Card title={t("groups.create.capacity")}>
      <Details>
        <DetailRow label={t("groups.create.servers")}>
          {format.number(maxInstances)}
        </DetailRow>
        <DetailRow label={t("groups.create.slots")}>
          {t("groups.create.slotsValue", {
            slots: format.number(maxInstances * playersPerServer),
            each: format.number(playersPerServer),
          })}
        </DetailRow>
        <DetailRow label={t("groups.create.memory")}>
          {gib === null
            ? "—"
            : t("groups.create.memoryValue", {
                total: amount(gib * maxInstances),
                each: amount(gib),
              })}
        </DetailRow>
      </Details>
      <p className="mt-3 text-small text-text-3">
        {t("groups.create.capacityHint")}
      </p>
    </Card>
  );
}
