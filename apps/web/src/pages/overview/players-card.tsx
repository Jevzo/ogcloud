import { useOverviewHistory } from "@ogcloud/api-client/react";
import { Card, Segmented, Skeleton, TimeSeriesChart } from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useFormat } from "../../format.ts";
import { useMediaQuery } from "../../media.ts";

const HOUR_MS = 3_600_000;
const RANGES = { "24h": 24, "6h": 6, "1h": 1 } as const;
type Range = keyof typeof RANGES;
// The controller samples once a minute (D-0213).
const HISTORY_REFRESH_MS = 60_000;
// Large monitors get a taller chart, so a day of samples does not flatten into a strip (D-0170).
const CHART_HEIGHT = 150;
const TALL_CHART_HEIGHT = 260;
const WIDE_SCREEN = "(min-width: 1920px)";

/** Players online now against the network's slots, and over the last day. */
export function PlayersCard({ online, max }: { online: number; max: number }) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const [range, setRange] = useState<Range>("24h");
  const height = useMediaQuery(WIDE_SCREEN) ? TALL_CHART_HEIGHT : CHART_HEIGHT;
  const history = useOverviewHistory(undefined, {
    query: { refetchInterval: HISTORY_REFRESH_MS },
  });
  const now = Date.now();
  const points = (history.data?.data.points ?? []).map((point) => ({
    at: new Date(point.at),
    value: point.players,
  }));
  const shown = points.filter(
    (point) => point.at.getTime() >= now - RANGES[range] * HOUR_MS,
  );
  const hourAgo = points.find((point) => point.at.getTime() >= now - HOUR_MS);

  return (
    <Card
      title={t("overview.players.title")}
      actions={
        <Segmented
          label={t("overview.players.range")}
          options={Object.keys(RANGES).map((value) => ({
            value: value as Range,
            label: t(`overview.players.ranges.${value as Range}`),
          }))}
          value={range}
          onValueChange={setRange}
        />
      }
    >
      <div className="mb-3 flex flex-wrap items-baseline gap-x-2">
        <span className="text-display tabular-nums">
          {format.number(online)}
        </span>
        <span className="text-text-2">
          {t("overview.players.of", { max: format.number(max) })}
        </span>
        {hourAgo === undefined ? null : (
          <span className="ml-auto text-small text-text-3 tabular-nums">
            {t("overview.players.change", {
              change: format.signed(online - hourAgo.value),
            })}
          </span>
        )}
      </div>
      {history.isPending ? (
        <div style={{ height }}>
          <Skeleton className="h-full" />
        </div>
      ) : shown.length < 2 ? (
        <p
          className="grid place-items-center text-small text-text-3"
          style={{ height }}
        >
          {t("overview.players.empty")}
        </p>
      ) : (
        <TimeSeriesChart
          label={t("overview.players.chart", {
            range: t(`overview.players.ranges.${range}`),
          })}
          series={[{ name: t("overview.players.series"), points: shown }]}
          height={height}
          formatValue={format.number}
          formatTime={format.clock}
        />
      )}
    </Card>
  );
}
