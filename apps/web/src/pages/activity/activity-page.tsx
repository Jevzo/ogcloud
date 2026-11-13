import { PageHeader, Tabs } from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useAlerts } from "../../alerts.ts";
import { AlertsTab } from "./alerts-tab.tsx";
import { AuditTab } from "./audit-tab.tsx";
import { JobsTab } from "./jobs-tab.tsx";
import { ScalingTab } from "./scaling-tab.tsx";

type Section = "audit" | "scaling" | "jobs" | "alerts";

/** What happened on the network: changes, scaling decisions, long operations and alerts. */
export function ActivityPage() {
  const { t } = useTranslation("web");
  const { unseen } = useAlerts();
  const [section, setSection] = useState<Section>(
    unseen > 0 ? "alerts" : "audit",
  );
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("nav.activity")} />
      <Tabs
        label={t("activity.sections")}
        value={section}
        onValueChange={setSection}
        tabs={[
          {
            value: "audit",
            label: t("activity.tabs.audit"),
            content: <AuditTab />,
          },
          {
            value: "scaling",
            label: t("activity.tabs.scaling"),
            content: <ScalingTab />,
          },
          {
            value: "jobs",
            label: t("activity.tabs.jobs"),
            content: <JobsTab />,
          },
          {
            value: "alerts",
            label:
              unseen > 0
                ? t("activity.tabs.alertsNew", { count: unseen })
                : t("activity.tabs.alerts"),
            content: <AlertsTab />,
          },
        ]}
      />
    </div>
  );
}
