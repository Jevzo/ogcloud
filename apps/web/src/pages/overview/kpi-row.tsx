import type {
  LoadPointView,
  OverviewView,
  ServerViewState,
} from "@ogcloud/api-client";
import { type KpiDelta, KpiTile } from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useFormat } from "../../format.ts";
import { sampleHourAgo } from "../../history.ts";

// A day of minute samples thinned for a sparkline a few hundred pixels wide.
const SPARK_POINTS = 48;
const STARTING: readonly ServerViewState[] = [
  "REQUESTED",
  "PREPARING",
  "STARTING",
];
const DRAINING: readonly ServerViewState[] = ["DRAINING", "STOPPING"];

interface KpiRowProps {
  data: OverviewView;
  /** The last day's samples, oldest first; empty until the controller has sampled. */
  history: readonly LoadPointView[];
}

/** The overview's verdict: players online first, then servers and groups, each against its baseline (D-0231). */
export function KpiRow({ data, history }: KpiRowProps) {
  const { t, i18n } = useTranslation("web");
  const format = useFormat();
  const list = new Intl.ListFormat(i18n.language, { type: "conjunction" });
  const count = (states: readonly ServerViewState[]) =>
    states.reduce((sum, state) => sum + (data.serversByState[state] ?? 0), 0);
  const running = data.serversByState.RUNNING ?? 0;
  const starting = count(STARTING);
  const draining = count(DRAINING);
  const paused = data.groups.filter((group) => group.crashLoopPaused).length;
  const maintenance = data.groups.filter((group) => group.maintenance).length;
  const hourAgo = sampleHourAgo(history, Date.now());
  // More players is good news; more servers is neither.
  const changeOf = (change: number, judged: boolean): KpiDelta => ({
    change: format.signed(change),
    basis: t("overview.kpi.basis"),
    trend: change > 0 ? "up" : change < 0 ? "down" : "flat",
    ...(judged && change !== 0 ? { good: change > 0 } : {}),
  });
  const delta = (now: number, before: number | undefined, judged: boolean) =>
    before === undefined ? undefined : changeOf(now - before, judged);
  const spark = (pick: (point: LoadPointView) => number) => {
    const step = Math.max(1, Math.floor(history.length / SPARK_POINTS));
    return history.filter((_, index) => index % step === 0).map(pick);
  };
  const serverNotes = [
    starting > 0 ? t("overview.kpi.starting", { count: starting }) : null,
    draining > 0 ? t("overview.kpi.draining", { count: draining }) : null,
  ].filter((note) => note !== null);
  const groupNotes = [
    paused > 0 ? t("overview.kpi.paused", { count: paused }) : null,
    maintenance > 0
      ? t("overview.kpi.maintenance", { count: maintenance })
      : null,
  ].filter((note) => note !== null);

  return (
    <div className="grid grid-cols-3 gap-6">
      <TileLink to="/players">
        <KpiTile
          label={t("overview.kpi.players")}
          value={data.playersOnline}
          format={format.number}
          caption={t("overview.kpi.slots", {
            max: format.number(data.maxPlayers),
          })}
          delta={delta(data.playersOnline, hourAgo?.players, true)}
          history={spark((point) => point.players)}
        />
      </TileLink>
      <TileLink to="/servers">
        <KpiTile
          label={t("overview.kpi.servers")}
          value={running}
          format={format.number}
          caption={
            serverNotes.length === 0 ? undefined : list.format(serverNotes)
          }
          delta={delta(running, hourAgo?.running, false)}
          history={spark((point) => point.running)}
        />
      </TileLink>
      <TileLink to="/groups">
        <KpiTile
          label={t("overview.kpi.groups")}
          value={data.groups.length}
          format={format.number}
          caption={
            groupNotes.length === 0
              ? t("overview.kpi.allOnline")
              : list.format(groupNotes)
          }
          warn={paused > 0}
        />
      </TileLink>
    </div>
  );
}

/** A tile is the way into its page, so it rises under the pointer. */
function TileLink({
  to,
  children,
}: {
  to: "/players" | "/servers" | "/groups";
  children: ReactNode;
}) {
  return (
    <Link to={to} className="lift block rounded-card [&>section]:h-full">
      {children}
    </Link>
  );
}
