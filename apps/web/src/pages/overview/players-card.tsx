import { useOverviewHistory } from "@ogcloud/api-client/react";
import { Card, Loading, Segmented, TimeSeriesChart } from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { loadFailure } from "../../errors.ts";
import { useFormat } from "../../format.ts";
import { LoadFailed } from "../../load-failed.tsx";
import { useMediaQuery } from "../../media.ts";
import { placeholderHistory } from "../../placeholders.ts";

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
  const loading = history.data === undefined;
  const points = (history.data?.data ?? placeholderHistory(now)).points.map(
    (point) => ({
      at: new Date(point.at),
      value: point.players,
    }),
  );
  const shown = points.filter(
    (point) => point.at.getTime() >= now - RANGES[range] * HOUR_MS,
  );
  const failure = loadFailure(history);
  const hourAgo = points.find((point) => point.at.getTime() >= now - HOUR_MS);

  return (
    <Card
      title={t("overview.players.title")}
      actions={
        <Segmented
          size="sm"
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
        <span data-skeleton="" className="text-display tabular-nums">
          {format.number(online)}
        </span>
        <span data-skeleton="" className="text-text-2">
          {t("overview.players.of", { max: format.number(max) })}
        </span>
        {hourAgo === undefined ? null : (
          <Loading when={loading}>
            <span
              data-skeleton=""
              className="ml-auto text-small text-text-3 tabular-nums"
            >
              {online === hourAgo.value
                ? t("overview.players.unchanged")
                : t("overview.players.change", {
                    change: format.signed(online - hourAgo.value),
                  })}
            </span>
          </Loading>
        )}
      </div>
      {failure !== undefined && history.data === undefined ? (
        <LoadFailed failure={failure} />
      ) : shown.length < 2 ? (
        <p
          className="grid place-items-center text-small text-text-3"
          style={{ height }}
        >
          {t("overview.players.empty")}
        </p>
      ) : (
        <Loading when={loading}>
          <TimeSeriesChart
            label={t("overview.players.chart", {
              range: t(`overview.players.ranges.${range}`),
            })}
            series={[{ name: t("overview.players.series"), points: shown }]}
            height={height}
            formatValue={format.number}
            formatTime={format.clock}
          />
        </Loading>
      )}
    </Card>
  );
}
