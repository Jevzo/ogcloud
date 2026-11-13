import type { LoadHistoryView } from "@ogcloud/api-client";
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
// Large monitors get a taller chart, so a day of samples does not flatten into a strip (D-0170).
const CHART_HEIGHT = 180;
const TALL_CHART_HEIGHT = 280;
const WIDE_SCREEN = "(min-width: 1920px)";

interface PlayersCardProps {
  /** The overview's history query, shared with the KPI row. */
  history: {
    data: { data: LoadHistoryView } | undefined;
    error: unknown;
    refetch: () => unknown;
  };
}

/** Players online over the last day, or a shorter range; the number now leads the page as a KPI. */
export function PlayersCard({ history }: PlayersCardProps) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const [range, setRange] = useState<Range>("24h");
  const height = useMediaQuery(WIDE_SCREEN) ? TALL_CHART_HEIGHT : CHART_HEIGHT;
  const now = Date.now();
  const loading = history.data === undefined;
  const points = (history.data?.data ?? placeholderHistory(now)).points
    .map((point) => ({ at: new Date(point.at), value: point.players }))
    .filter((point) => point.at.getTime() >= now - RANGES[range] * HOUR_MS);
  const failure = loadFailure(history);
  const rangeName = t(`overview.players.ranges.${range}`);

  return (
    <Card
      title={t("overview.players.title", { range: rangeName })}
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
      {failure !== undefined && history.data === undefined ? (
        <LoadFailed failure={failure} />
      ) : points.length < 2 ? (
        <p
          className="grid place-items-center text-small text-text-3"
          style={{ height }}
        >
          {t("overview.players.empty")}
        </p>
      ) : (
        <Loading when={loading}>
          <TimeSeriesChart
            label={t("overview.players.chart", { range: rangeName })}
            series={[{ name: t("overview.players.series"), points }]}
            height={height}
            formatValue={format.number}
            formatTime={format.clock}
          />
        </Loading>
      )}
    </Card>
  );
}
