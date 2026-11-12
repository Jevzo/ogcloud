import { Badge } from "@ogcloud/ui";
import { Construction } from "lucide-react";
import { useTranslation } from "react-i18next";

/** Shown by the Overview and Network titles while only players with the bypass permission can join (D-0228). */
export function MaintenanceBadge({ maintenance }: { maintenance: boolean }) {
  const { t } = useTranslation("web");
  return maintenance ? (
    <Badge tone="warning" icon={Construction}>
      {t("network.look.maintenance")}
    </Badge>
  ) : null;
}
