import type { GroupOverview } from "@ogcloud/api-client";
import { Badge, type Tone } from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import {
  CircleCheck,
  CircleDashed,
  Construction,
  type LucideIcon,
  OctagonPause,
} from "lucide-react";
import { useTranslation } from "react-i18next";

type GroupStatus = "paused" | "maintenance" | "online" | "idle";

const STATUS_LOOK: Record<GroupStatus, { tone: Tone; icon: LucideIcon }> = {
  paused: { tone: "danger", icon: OctagonPause },
  maintenance: { tone: "warning", icon: Construction },
  online: { tone: "success", icon: CircleCheck },
  idle: { tone: "neutral", icon: CircleDashed },
};

function statusOf(group: GroupOverview): GroupStatus {
  if (group.crashLoopPaused) return "paused";
  if (group.maintenance) return "maintenance";
  return group.running > 0 ? "online" : "idle";
}

/** A group's state at a glance: paused starts first, then maintenance, then whether it runs anything. */
export function GroupStatusBadge({ group }: { group: GroupOverview }) {
  const { t } = useTranslation("web");
  const status = statusOf(group);
  return <Badge {...STATUS_LOOK[status]}>{t(`groups.status.${status}`)}</Badge>;
}

export function GroupLink({ id }: { id: string }) {
  return (
    <Link
      to="/groups/$groupId"
      params={{ groupId: id }}
      className="font-label hover:underline"
    >
      {id}
    </Link>
  );
}
