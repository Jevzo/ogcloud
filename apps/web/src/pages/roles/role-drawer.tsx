import type { RoleView } from "@ogcloud/api-client";
import { useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { DetailRow } from "../../details.tsx";
import { useHeld } from "../../held.ts";
import { RecordDrawer } from "../../record-drawer.tsx";

/** A list row's role beside the list, with the way to its page (D-0231). */
export function RoleDrawer({
  role: current,
  onClose,
}: {
  role: RoleView | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  // The drawer slides out still showing what it showed.
  const role = useHeld(current);
  if (role === null) return null;
  return (
    <RecordDrawer
      open={current !== null}
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
