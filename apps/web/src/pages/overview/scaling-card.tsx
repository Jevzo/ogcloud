import type { ScalingLogView } from "@ogcloud/api-client";
import {
  ArrowDownToLine,
  type LucideIcon,
  Play,
  RotateCcw,
  Square,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { EventItem, EventsCard } from "../../event-item.tsx";

const ACTION_LOOK: Record<
  ScalingLogView["action"],
  { icon: LucideIcon; tint: string }
> = {
  START: { icon: Play, tint: "bg-success-soft text-success" },
  DRAIN: { icon: ArrowDownToLine, tint: "bg-warning-soft text-warning" },
  UNDRAIN: { icon: RotateCcw, tint: "bg-info-soft text-info" },
  STOP: { icon: Square, tint: "bg-surface-3 text-text-2" },
};

/** The autoscaler's latest decisions, newest first. */
export function ScalingCard({
  entries,
}: {
  entries: readonly ScalingLogView[];
}) {
  const { t } = useTranslation("web");

  return (
    <EventsCard
      title={t("overview.scaling.title")}
      empty={t("overview.scaling.empty")}
      section="scaling"
    >
      {entries.map((entry) => (
        <EventItem
          key={entry.id}
          {...ACTION_LOOK[entry.action]}
          title={t(`overview.scaling.actions.${entry.action}`, {
            group: entry.groupId,
          })}
          detail={t(`overview.scaling.reasons.${entry.reason}`)}
          at={entry.occurredAt}
        />
      ))}
    </EventsCard>
  );
}
