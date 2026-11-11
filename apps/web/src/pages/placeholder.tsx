import { EmptyState, PageHeader } from "@ogcloud/ui";
import { Hammer } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { NavLabel } from "../navigation.ts";

/** Stands in for a page until its ticket builds it (T-0706 to T-0709). */
export function Placeholder({ label }: { label: NavLabel | "account" }) {
  const { t } = useTranslation("web");
  const title = label === "account" ? t("pages.account") : t(`nav.${label}`);
  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={title} />
      <EmptyState
        icon={Hammer}
        title={title}
        description={t("pages.placeholder")}
      />
    </div>
  );
}
