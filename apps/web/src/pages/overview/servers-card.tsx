import type {
  GroupOverview,
  OverviewView,
  ServerViewState,
} from "@ogcloud/api-client";
import { Button, Card, Note, type Segment, StackedBar } from "@ogcloud/ui";
import {
  CircleCheck,
  CirclePause,
  LoaderCircle,
  type LucideIcon,
  TriangleAlert,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { useFormat } from "../../format.ts";
import { useCan } from "../../permissions.ts";

interface Phase {
  key: "running" | "starting" | "draining";
  states: readonly ServerViewState[];
  tone: Segment["tone"];
  icon: LucideIcon;
  text: string;
}

// Servers by where they are in their life; stopped ones are gone from live state.
const PHASES: readonly Phase[] = [
  {
    key: "running",
    states: ["RUNNING"],
    tone: "success",
    icon: CircleCheck,
    text: "text-success",
  },
  {
    key: "starting",
    states: ["REQUESTED", "PREPARING", "STARTING"],
    tone: "info",
    icon: LoaderCircle,
    text: "text-info",
  },
  {
    key: "draining",
    states: ["DRAINING", "STOPPING"],
    tone: "warning",
    icon: CirclePause,
    text: "text-warning",
  },
];

interface ServersCardProps {
  byState: OverviewView["serversByState"];
  paused: readonly GroupOverview[];
  onResume: (groupId: string) => void;
}

/** How many servers run, start and drain, and which groups stopped starting servers. */
export function ServersCard({ byState, paused, onResume }: ServersCardProps) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const canResume = useCan("groups:write");
  const counts = PHASES.map((phase) => ({
    ...phase,
    count: phase.states.reduce((sum, state) => sum + (byState[state] ?? 0), 0),
  }));
  const total = counts.reduce((sum, phase) => sum + phase.count, 0);

  return (
    <Card
      title={t("overview.servers.title")}
      actions={
        <span data-skeleton="" className="text-small text-text-3 tabular-nums">
          {t("overview.servers.total", { count: total })}
        </span>
      }
    >
      <div className="flex flex-col gap-3">
        <StackedBar
          segments={counts.map((phase) => ({
            label: t(`overview.servers.${phase.key}`),
            value: phase.count,
            tone: phase.tone,
          }))}
        />
        <ul className="flex flex-col">
          {counts.map(({ key, icon: Icon, text, count }) => (
            <li key={key} className="flex h-row items-center gap-2.5">
              <Icon
                aria-hidden="true"
                size={16}
                strokeWidth={1.75}
                className={text}
              />
              <span className="flex-1">{t(`overview.servers.${key}`)}</span>
              <span data-skeleton="" className="font-label tabular-nums">
                {format.number(count)}
              </span>
            </li>
          ))}
        </ul>
        {paused.map((group) => (
          <Note
            key={group.id}
            tone="danger"
            icon={TriangleAlert}
            action={
              canResume ? (
                <Button size="sm" onClick={() => onResume(group.id)}>
                  {t("groups.actions.resume")}
                </Button>
              ) : undefined
            }
          >
            <div className="font-label">
              {t("overview.servers.paused", { id: group.id })}
            </div>
            <div className="text-small text-text-2">
              {t("overview.servers.pausedReason")}
            </div>
          </Note>
        ))}
      </div>
    </Card>
  );
}
