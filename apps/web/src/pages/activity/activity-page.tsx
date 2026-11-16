import { PageHeader, Tabs } from "@ogcloud/ui";
import { getRouteApi } from "@tanstack/react-router";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useAlerts } from "../../alerts.ts";
import { AlertsTab } from "./alerts-tab.tsx";
import { AuditTab } from "./audit-tab.tsx";
import { JobsTab } from "./jobs-tab.tsx";
import { ScalingTab } from "./scaling-tab.tsx";

const SECTIONS = ["audit", "scaling", "jobs", "alerts"] as const;

/** A tab of the Activity page; a link can open the page on it. */
export type ActivitySection = (typeof SECTIONS)[number];

/** The Activity page's search: the tab it shows, left out for the default. */
export function activitySearch(search: Record<string, unknown>): {
  section?: ActivitySection;
} {
  const section = SECTIONS.find((value) => value === search.section);
  return section === undefined ? {} : { section };
}

const route = getRouteApi("/app/activity");

/** What happened on the network: changes, scaling decisions, long operations and alerts. */
export function ActivityPage() {
  const { t } = useTranslation("web");
  const { unseen } = useAlerts();
  const navigate = route.useNavigate();
  const opened = route.useSearch().section;
  const [section, setSection] = useState<ActivitySection>(
    opened ?? (unseen > 0 ? "alerts" : "audit"),
  );
  // The URL follows the tab, so a reload stays on it; replaced, so tabs add no history entries.
  const show = (next: ActivitySection) => {
    setSection(next);
    void navigate({ search: { section: next }, replace: true });
  };
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("nav.activity")} />
      <Tabs
        label={t("activity.sections")}
        value={section}
        onValueChange={show}
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
