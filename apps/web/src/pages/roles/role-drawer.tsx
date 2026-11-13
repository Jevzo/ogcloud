import type { RoleView } from "@ogcloud/api-client";
import { useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { DetailRow } from "../../details.tsx";
import { RecordDrawer } from "../../record-drawer.tsx";

/** A list row's role beside the list, with the way to its page (D-0231). */
export function RoleDrawer({
  role,
  onClose,
}: {
  role: RoleView | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  if (role === null) return null;
  return (
    <RecordDrawer
      title={role.name}
      onClose={onClose}
      onOpenPage={() =>
        void navigate({
          to: "/roles/$roleId",
          params: { roleId: role.id },
        })
      }
    >
      <DetailRow label={t("roles.columns.kind")}>
        {role.builtIn ? t("roles.builtIn") : t("roles.custom")}
      </DetailRow>
      <DetailRow label={t("roles.columns.id")}>
        <span className="font-mono text-mono">{role.id}</span>
      </DetailRow>
      <DetailRow label={t("roles.columns.permissions")}>
        {role.permissions.length}
      </DetailRow>
    </RecordDrawer>
  );
}
