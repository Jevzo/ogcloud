import type { ServerViewState } from "@ogcloud/api-client";
import { Badge, type Tone } from "@ogcloud/ui";
import {
  CircleCheck,
  CircleDashed,
  CirclePause,
  CircleStop,
  LoaderCircle,
  type LucideIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";

const STATE_LOOK: Record<ServerViewState, { tone: Tone; icon: LucideIcon }> = {
  REQUESTED: { tone: "info", icon: LoaderCircle },
  PREPARING: { tone: "info", icon: LoaderCircle },
  STARTING: { tone: "info", icon: LoaderCircle },
  RUNNING: { tone: "success", icon: CircleCheck },
  DRAINING: { tone: "warning", icon: CirclePause },
  STOPPING: { tone: "warning", icon: CircleStop },
  STOPPED: { tone: "neutral", icon: CircleDashed },
};

/** A server's lifecycle state with its colour and icon, the same in every list and page. */
export function ServerStateBadge({ state }: { state: ServerViewState }) {
  const { t } = useTranslation("web");
  return <Badge {...STATE_LOOK[state]}>{t(`servers.state.${state}`)}</Badge>;
}
