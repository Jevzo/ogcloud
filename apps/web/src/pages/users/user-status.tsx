import { Badge } from "@ogcloud/ui";
import { CircleCheck, CircleSlash } from "lucide-react";
import { useTranslation } from "react-i18next";

/** Whether the user may sign in; a disabled user's logins ended when they were disabled. */
export function UserStatusBadge({ enabled }: { enabled: boolean }) {
  const { t } = useTranslation("web");
  return enabled ? (
    <Badge tone="success" icon={CircleCheck}>
      {t("users.status.enabled")}
    </Badge>
  ) : (
    <Badge tone="neutral" icon={CircleSlash}>
      {t("users.status.disabled")}
    </Badge>
  );
}
