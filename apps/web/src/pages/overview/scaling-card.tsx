import type { ScalingLogView } from "@ogcloud/api-client";
import { Card } from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import {
  ArrowDownToLine,
  type LucideIcon,
  Play,
  RotateCcw,
  Square,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { ACTIVITY_LINK, EventItem } from "../../event-item.tsx";

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
    <Card
      title={t("overview.scaling.title")}
      actions={
        <Link to="/activity" className={ACTIVITY_LINK}>
          {t("overview.scaling.all")}
        </Link>
      }
    >
      {entries.length === 0 ? (
        <p className="text-small text-text-3">{t("overview.scaling.empty")}</p>
      ) : (
        <ol className="flex flex-col">
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
        </ol>
      )}
    </Card>
  );
}
