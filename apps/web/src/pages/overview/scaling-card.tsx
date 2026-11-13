import type { ScalingLogView } from "@ogcloud/api-client";
import { Card, cn } from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import {
  ArrowDownToLine,
  type LucideIcon,
  Play,
  RotateCcw,
  Square,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { useFormat } from "../../format.ts";

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
  const format = useFormat();
  const now = Date.now();

  return (
    <Card
      title={t("overview.scaling.title")}
      actions={
        <Link
          to="/activity"
          className="font-label text-accent text-small hover:underline"
        >
          {t("overview.scaling.all")}
        </Link>
      }
    >
      {entries.length === 0 ? (
        <p className="text-small text-text-3">{t("overview.scaling.empty")}</p>
      ) : (
        <ol className="flex flex-col">
          {entries.map((entry) => {
            const { icon: Icon, tint } = ACTION_LOOK[entry.action];
            const at = new Date(entry.occurredAt);
            const title = t(`overview.scaling.actions.${entry.action}`, {
              group: entry.groupId,
            });
            const reason = t(`overview.scaling.reasons.${entry.reason}`);
            return (
              <li key={entry.id} className="flex items-center gap-3 py-1.5">
                <span
                  aria-hidden="true"
                  data-skeleton=""
                  className={cn(
                    "grid size-7 shrink-0 place-items-center rounded-full",
                    tint,
                  )}
                >
                  <Icon size={14} strokeWidth={2} />
                </span>
                <span className="flex min-w-0 flex-1 flex-col">
                  <span data-skeleton="" className="truncate" title={title}>
                    {title}
                  </span>
                  <span
                    data-skeleton=""
                    className="truncate text-meta text-text-3"
                    title={reason}
                  >
                    {reason}
                  </span>
                </span>
                <time
                  data-skeleton=""
                  dateTime={entry.occurredAt}
                  className="shrink-0 text-meta text-text-3"
                >
                  {format.ago(at, now)}
                </time>
              </li>
            );
          })}
        </ol>
      )}
    </Card>
  );
}
