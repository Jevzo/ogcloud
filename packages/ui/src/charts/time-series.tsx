import { scaleLinear, scaleTime } from "d3-scale";
import { area, curveMonotoneX, line } from "d3-shape";
import { type PointerEvent, useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLoading } from "../components/loading.tsx";
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

/** Series take the chart colours in this fixed order; callers fold anything past five into "Other" (D-0231). */
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
const DIMMED = 0.35;
// A single series' fill fades from this opacity at the line down to nothing.
const FILL_OPACITY = 0.2;

type Scales = ReturnType<typeof scalesOf>;

/** Where every value lands: the time and value scales and the gridlines, for one set of series. */
function scalesOf(series: readonly Series[], width: number, height: number) {
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
  return { times, x, y, yTicks };
}

interface PlotProps {
  series: readonly Series[];
  scales: Scales;
  width: number;
  height: number;
  formatValue: (value: number) => string;
  formatTime: (at: Date) => string;
  /** The ids of the chart's fills and its draw-in clip. */
  id: string;
  dim: (name: string) => number;
  onFocus: (name: string | null) => void;
}

/** The gridlines, axis labels and series of one set of data; a data change fades one plot into the next. */
function Plot({
  series,
  scales: { x, y, yTicks },
  width,
  height,
  formatValue,
  formatTime,
  id,
  dim,
  onFocus,
}: PlotProps) {
  const toLine = line<Point>()
    .x((point) => x(point.at))
    .y((point) => y(point.value))
    .curve(curveMonotoneX);
  const toArea = area<Point>()
    .x((point) => x(point.at))
    .y0(y(0))
    .y1((point) => y(point.value))
    .curve(curveMonotoneX);
  return (
    <>
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
        <g
          key={s.name}
          clipPath={`url(#${id}-reveal)`}
          onPointerEnter={() => onFocus(s.name)}
          onPointerLeave={() => onFocus(null)}
          className="transition-opacity"
          style={{ opacity: dim(s.name) }}
        >
          {series.length === 1 ? (
            <path
              d={toArea(s.points as Point[]) ?? ""}
              fill={`url(#${id}-fill-${index})`}
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
    </>
  );
}

/** What a set of series shows, so a new array of the same data counts as no change. */
function signatureOf(series: readonly Series[]): string {
  return series
    .map(
      (s) =>
        `${s.name}:${s.points.map((point) => `${point.at.getTime()}=${point.value}`).join(",")}`,
    )
    .join("|");
}

/**
 * When the data changes, the plot it replaces stays a moment to fade out under the new one fading in, so a new range
 * or a rescale moves smoothly instead of jumping; opacity only (D-0231).
 */
function useLeavingPlot(series: readonly Series[], loading: boolean) {
  const signature = signatureOf(series);
  // Placeholder data never fades out over the first real data.
  const last = useRef<{
    signature: string;
    series: readonly Series[];
    loading: boolean;
  }>({ signature, series, loading });
  const [leaving, setLeaving] = useState<{
    version: number;
    series: readonly Series[];
  } | null>(null);
  useEffect(() => {
    const before = last.current;
    last.current = { signature, series, loading };
    if (before.signature === signature || before.loading || loading) return;
    setLeaving((current) => ({
      version: (current?.version ?? 0) + 1,
      series: before.series,
    }));
  }, [signature, series, loading]);
  return { leaving, gone: () => setLeaving(null) };
}

interface TimeSeriesChartProps {
  /** Names the chart for screen readers and titles its table view. */
  label: string;
  series: readonly Series[];
  height: number;
  formatValue: (value: number) => string;
  formatTime: (at: Date) => string;
  className?: string;
}

/** Values over time: a filled area for one series, lines for up to five, with a crosshair; screen readers get a table. */
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
  const [hover, setHover] = useState<number | null>(null);
  // The series under the pointer, in the chart or its legend; the others step back (D-0231).
  const [focus, setFocus] = useState<string | null>(null);
  // useId's characters are not valid in a url(#…) reference.
  const id = `og-chart-${useId().replace(/[^\w-]/g, "")}`;
  const dim = (name: string) => (focus !== null && focus !== name ? DIMMED : 1);
  const loading = useLoading();
  const { leaving, gone } = useLeavingPlot(series, loading);
  const scales = scalesOf(series, width, height);
  const { times, x, y } = scales;
  const plot = {
    width,
    height,
    formatValue,
    formatTime,
    id,
    dim,
    onFocus: setFocus,
  };

  const track = (event: PointerEvent<SVGSVGElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    setHover(nearestIndex(times, x.invert(event.clientX - bounds.left)));
  };
  const hoverTime = hover === null ? undefined : times[hover];

  return (
    <figure className={cn("relative m-0", className)}>
      {series.length > 1 ? (
        <ul className="mb-2 flex flex-wrap gap-x-4 gap-y-1 text-meta text-text-2">
          {series.map((s, index) => (
            <li
              key={s.name}
              onPointerEnter={() => setFocus(s.name)}
              onPointerLeave={() => setFocus(null)}
              className="flex items-center gap-1.5 transition-opacity"
              style={{ opacity: dim(s.name) }}
            >
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
      <div ref={frame} data-skeleton="" className="relative" style={{ height }}>
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
            <defs>
              {series.map((s, index) => (
                <linearGradient
                  key={s.name}
                  id={`${id}-fill-${index}`}
                  x1="0"
                  x2="0"
                  y1="0"
                  y2="1"
                >
                  <stop
                    offset="0"
                    stopColor={`var(${SERIES_COLOURS[index]})`}
                    stopOpacity={FILL_OPACITY}
                  />
                  <stop
                    offset="1"
                    stopColor={`var(${SERIES_COLOURS[index]})`}
                    stopOpacity={0}
                  />
                </linearGradient>
              ))}
              {/* The data draws in once, left to right, when the chart first appears. */}
              <clipPath id={`${id}-reveal`}>
                <rect
                  // Mounted anew when loading ends, so the draw-in plays for the real data, not the skeleton.
                  key={loading ? "loading" : "ready"}
                  width={width}
                  height={height}
                  className="origin-left animate-draw-in [transform-box:fill-box]"
                />
              </clipPath>
            </defs>
            {leaving === null ? null : (
              <g
                key={`leaving-${leaving.version}`}
                onAnimationEnd={gone}
                className="pointer-events-none animate-fade-out [animation-fill-mode:forwards]"
              >
                <Plot
                  series={leaving.series}
                  scales={scalesOf(leaving.series, width, height)}
                  {...plot}
                />
              </g>
            )}
            <g
              key={`current-${leaving?.version ?? 0}`}
              className={leaving === null ? undefined : "animate-fade-in"}
            >
              <Plot series={series} scales={scales} {...plot} />
            </g>
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
            className="pointer-events-none absolute top-1 rounded-control border border-border bg-floating px-2.5 py-1.5 shadow-popover"
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
      <div className="sr-only">
        <table className="w-full text-table tabular-nums">
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
