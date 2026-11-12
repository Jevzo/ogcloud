import { scaleLinear, scaleTime } from "d3-scale";
import { area, curveMonotoneX, line } from "d3-shape";
import { type PointerEvent, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "../lib/cn.ts";
import { useWidth } from "../lib/use-width.ts";

export interface Point {
  at: Date;
  value: number;
}

export interface Series {
  name: string;
  points: readonly Point[];
}

/** Series take the chart colours in this fixed order; callers fold anything past five into "Other" (D-0216). */
const SERIES_COLOURS = [
  "--og-chart-1",
  "--og-chart-2",
  "--og-chart-3",
  "--og-chart-4",
  "--og-chart-5",
] as const;
const PADDING = { top: 8, right: 44, bottom: 22, left: 0 };
const GRID_LINES = 4;
// Time labels need about this much room each, so narrow charts get fewer.
const X_TICK_SPACING = 120;
const MIN_X_TICKS = 2;

interface TimeSeriesChartProps {
  /** Names the chart for screen readers and titles its table view. */
  label: string;
  series: readonly Series[];
  height: number;
  formatValue: (value: number) => string;
  formatTime: (at: Date) => string;
  className?: string;
}

/** Values over time: a filled area for one series, lines for up to five, with a crosshair and a table view. */
export function TimeSeriesChart({
  label,
  series,
  height,
  formatValue,
  formatTime,
  className,
}: TimeSeriesChartProps) {
  const { t } = useTranslation("ui");
  const frame = useRef<HTMLDivElement>(null);
  const width = useWidth(frame);
  const tableId = useId();
  const [asTable, setAsTable] = useState(false);
  const [hover, setHover] = useState<number | null>(null);

  const times = series[0]?.points.map((point) => point.at) ?? [];
  const max = Math.max(
    1,
    ...series.flatMap((s) => s.points.map((point) => point.value)),
  );
  const x = scaleTime()
    .domain([times[0] ?? new Date(0), times.at(-1) ?? new Date(0)])
    .range([PADDING.left, Math.max(PADDING.left, width - PADDING.right)]);
  const y = scaleLinear()
    .domain([0, max])
    .nice(GRID_LINES)
    .range([height - PADDING.bottom, PADDING.top]);
  // Counts get whole-number gridlines only: a scale showing 0.2 players reads as a fraction that never happens.
  const counts = series.every((s) =>
    s.points.every((point) => Number.isInteger(point.value)),
  );
  const yTicks = y
    .ticks(GRID_LINES)
    .filter((tick) => !counts || Number.isInteger(tick));
  const toLine = line<Point>()
    .x((point) => x(point.at))
    .y((point) => y(point.value))
    .curve(curveMonotoneX);
  const toArea = area<Point>()
    .x((point) => x(point.at))
    .y0(y(0))
    .y1((point) => y(point.value))
    .curve(curveMonotoneX);

  const track = (event: PointerEvent<SVGSVGElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    setHover(nearestIndex(times, x.invert(event.clientX - bounds.left)));
  };
  const hoverTime = hover === null ? undefined : times[hover];

  return (
    <figure className={cn("relative m-0", className)}>
      <div className="mb-1 flex justify-end">
        <button
          type="button"
          aria-controls={tableId}
          aria-expanded={asTable}
          onClick={() => setAsTable((shown) => !shown)}
          className="cursor-pointer rounded-control px-1.5 font-label text-accent-text text-meta hover:underline"
        >
          {asTable ? t("chart.showChart") : t("chart.showTable")}
        </button>
      </div>
      {series.length > 1 ? (
        <ul className="mb-2 flex flex-wrap gap-x-4 gap-y-1 text-meta text-text-2">
          {series.map((s, index) => (
            <li key={s.name} className="flex items-center gap-1.5">
              <span
                aria-hidden="true"
                className="h-0.5 w-3 rounded-full"
                style={{ backgroundColor: `var(${SERIES_COLOURS[index]})` }}
              />
              {s.name}
            </li>
          ))}
        </ul>
      ) : null}
      <div
        ref={frame}
        className={cn("relative", asTable && "hidden")}
        style={{ height }}
      >
        {width > 0 ? (
          <svg
            width={width}
            height={height}
            role="img"
            aria-label={label}
            onPointerMove={track}
            onPointerLeave={() => setHover(null)}
            className="block overflow-visible"
          >
            {yTicks.map((tick) => (
              <g key={tick}>
                <line
                  x1={PADDING.left}
                  x2={width - PADDING.right}
                  y1={y(tick)}
                  y2={y(tick)}
                  stroke="var(--og-border-subtle)"
                />
                <text
                  x={width}
                  y={y(tick)}
                  dy="0.32em"
                  textAnchor="end"
                  className="fill-text-3 text-meta tabular-nums"
                >
                  {formatValue(tick)}
                </text>
              </g>
            ))}
            {x
              .ticks(Math.max(MIN_X_TICKS, Math.floor(width / X_TICK_SPACING)))
              .map((tick) => (
                <text
                  key={tick.getTime()}
                  x={x(tick)}
                  y={height - 4}
                  textAnchor="middle"
                  className="fill-text-3 text-meta tabular-nums"
                >
                  {formatTime(tick)}
                </text>
              ))}
            {series.map((s, index) => (
              <g key={s.name}>
                {series.length === 1 ? (
                  <path
                    d={toArea(s.points as Point[]) ?? ""}
                    fill={`var(${SERIES_COLOURS[index]})`}
                    fillOpacity={0.12}
                  />
                ) : null}
                <path
                  d={toLine(s.points as Point[]) ?? ""}
                  fill="none"
                  stroke={`var(${SERIES_COLOURS[index]})`}
                  strokeWidth={2}
                  strokeLinejoin="round"
                />
              </g>
            ))}
            {hoverTime === undefined ? null : (
              <g>
                <line
                  x1={x(hoverTime)}
                  x2={x(hoverTime)}
                  y1={PADDING.top}
                  y2={height - PADDING.bottom}
                  stroke="var(--og-text-3)"
                  strokeDasharray="3 3"
                />
                {series.map((s, index) => {
                  const point = hover === null ? undefined : s.points[hover];
                  return point === undefined ? null : (
                    <circle
                      key={s.name}
                      cx={x(point.at)}
                      cy={y(point.value)}
                      r={4.5}
                      fill={`var(${SERIES_COLOURS[index]})`}
                      stroke="var(--og-surface)"
                      strokeWidth={2}
                    />
                  );
                })}
              </g>
            )}
          </svg>
        ) : null}
        {hoverTime === undefined ? null : (
          <div
            role="presentation"
            className="pointer-events-none absolute top-1 rounded-control border border-border bg-surface px-2.5 py-1.5 shadow-popover"
            style={
              x(hoverTime) > width / 2
                ? { right: width - x(hoverTime) + 12 }
                : { left: x(hoverTime) + 12 }
            }
          >
            <div className="text-meta text-text-3">{formatTime(hoverTime)}</div>
            {series.map((s) => {
              const point = hover === null ? undefined : s.points[hover];
              return point === undefined ? null : (
                <div key={s.name} className="font-bold tabular-nums">
                  {series.length > 1 ? `${s.name}: ` : ""}
                  {formatValue(point.value)}
                </div>
              );
            })}
          </div>
        )}
      </div>
      <div className={cn(!asTable && "sr-only")}>
        <table id={tableId} className="w-full text-table tabular-nums">
          <caption className="sr-only">{label}</caption>
          <thead>
            <tr>
              <th
                scope="col"
                className="h-row text-left font-label text-text-3"
              >
                {t("chart.time")}
              </th>
              {series.map((s) => (
                <th
                  key={s.name}
                  scope="col"
                  className="text-right font-label text-text-3"
                >
                  {s.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {times.map((at, index) => (
              <tr key={at.getTime()} className="border-border-subtle border-t">
                <td className="h-row">{formatTime(at)}</td>
                {series.map((s) => (
                  <td key={s.name} className="text-right">
                    {formatValue(s.points[index]?.value ?? 0)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  );
}

/** Binary search for the sample closest to a time; samples are sorted. */
function nearestIndex(times: readonly Date[], at: Date): number | null {
  if (times.length === 0) return null;
  let low = 0;
  let high = times.length - 1;
  while (high - low > 1) {
    const middle = (low + high) >> 1;
    if ((times[middle]?.getTime() ?? 0) < at.getTime()) low = middle;
    else high = middle;
  }
  const before = Math.abs((times[low]?.getTime() ?? 0) - at.getTime());
  const after = Math.abs((times[high]?.getTime() ?? 0) - at.getTime());
  return before <= after ? low : high;
}
