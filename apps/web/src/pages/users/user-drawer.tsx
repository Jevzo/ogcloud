import type { UserView } from "@ogcloud/api-client";
import { useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { DetailRow } from "../../details.tsx";
import { useHeld } from "../../held.ts";
import { RecordDrawer } from "../../record-drawer.tsx";
import { useRoleNames } from "./role-names.ts";
import { UserStatusBadge } from "./user-status.tsx";

/** A list row's user beside the list, with the way to its page (D-0231). */
export function UserDrawer({
  user: current,
  onClose,
}: {
  user: UserView | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const roles = useRoleNames();
  // The drawer slides out still showing what it showed.
  const user = useHeld(current);
  if (user === null) return null;
  return (
    <RecordDrawer
      open={current !== null}
      title={user.username}
      badges={<UserStatusBadge enabled={user.enabled} />}
      onClose={onClose}
      onOpenPage={() =>
        void navigate({
          to: "/users/$userId",
          params: { userId: user.id },
        })
      }
    >
      <DetailRow label={t("users.columns.email")}>{user.email}</DetailRow>
      <DetailRow label={t("users.columns.role")}>
        {roles.nameOf(user.roleId)}
      </DetailRow>
      <DetailRow label={t("users.columns.minecraft")}>
        {user.linkedPlayerName == null ? (
          <span className="text-text-3">{t("users.notLinked")}</span>
        ) : (
          <span className="font-mono text-mono">{user.linkedPlayerName}</span>
        )}
      </DetailRow>
      <DetailRow label={t("users.columns.created")}>
        <Ago at={user.createdAt} className="" />
      </DetailRow>
    </RecordDrawer>
  );
}
