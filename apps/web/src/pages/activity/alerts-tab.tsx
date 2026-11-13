import { Card, EmptyState } from "@ogcloud/ui";
import { BellOff, TriangleAlert } from "lucide-react";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { alertMessage, useAlerts } from "../../alerts.ts";
import { useFormat } from "../../format.ts";

/** Alerts that arrived while the dashboard was open; opening this marks them seen. */
export function AlertsTab() {
  const { t } = useTranslation("web");
  const format = useFormat();
  const { alerts, markSeen } = useAlerts();
  useEffect(() => markSeen(), [markSeen]);

  if (alerts.length === 0) {
    return (
      <EmptyState
        icon={BellOff}
        title={t("activity.alerts.empty")}
        description={t("activity.alerts.emptyDescription")}
      />
    );
  }
  return (
    <Card>
      <ol className="flex flex-col">
        {alerts.map((alert) => (
          <li
            key={`${alert.at}-${alert.subjectId}-${alert.kind}`}
            className="flex items-center gap-3 border-border-subtle border-t py-2 first:border-t-0"
          >
            <TriangleAlert
              aria-hidden="true"
              size={16}
              className="shrink-0 text-danger"
            />
            <span className="flex-1">{alertMessage(t, alert)}</span>
            <span className="text-meta text-text-3">
              {format.ago(new Date(alert.at), Date.now())}
            </span>
          </li>
        ))}
      </ol>
    </Card>
  );
}
