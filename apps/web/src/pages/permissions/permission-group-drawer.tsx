import type { PermissionGroupView } from "@ogcloud/api-client";
import { Badge } from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import { Star } from "lucide-react";
import { useTranslation } from "react-i18next";
import { DetailRow } from "../../details.tsx";
import { useHeld } from "../../held.ts";
import { RecordDrawer } from "../../record-drawer.tsx";
import { RankChip } from "./rank-look.tsx";

/** A list row's group beside the list, with the way to its page (D-0231). */
export function PermissionGroupDrawer({
  group: current,
  onClose,
}: {
  group: PermissionGroupView | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  // The drawer slides out still showing what it showed.
  const group = useHeld(current);
  if (group === null) return null;
  return (
    <RecordDrawer
      open={current !== null}
      title={group.settings.name}
      badges={
        group.settings.defaultGroup ? (
          <Badge tone="highlight" icon={Star}>
            {t("permissions.default")}
          </Badge>
        ) : null
      }
      onClose={onClose}
      onOpenPage={() =>
        void navigate({
          to: "/permissions/$permissionGroupId",
          params: { permissionGroupId: group.id },
        })
      }
    >
      <DetailRow label={t("permissions.columns.look")}>
        <RankChip display={group.settings.display} />
      </DetailRow>
      <DetailRow label={t("permissions.columns.id")}>
        <span className="font-mono text-mono">{group.id}</span>
      </DetailRow>
      <DetailRow label={t("permissions.columns.weight")}>
        {group.settings.weight}
      </DetailRow>
      <DetailRow label={t("permissions.columns.nodes")}>
        {Object.keys(group.settings.permissions).length}
      </DetailRow>
    </RecordDrawer>
  );
}
