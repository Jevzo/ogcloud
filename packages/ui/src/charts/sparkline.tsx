import { scaleLinear } from "d3-scale";
import { area, line } from "d3-shape";
import { useId } from "react";

const HEIGHT = 32;
const WIDTH = 120;
const STROKE = 1.5;

/** The shape of a recent history beside a KPI: no axes, the line in the accent over a fading fill. */
export function Sparkline({ values }: { values: readonly number[] }) {
  // useId's characters are not valid in a url(#…) reference.
  const gradient = `og-spark-${useId().replace(/[^\w-]/g, "")}`;
  if (values.length < 2) return null;
  const x = scaleLinear()
    .domain([0, values.length - 1])
    .range([0, WIDTH]);
  const top = Math.max(...values);
  const bottom = Math.min(...values);
  const y = scaleLinear()
    .domain(top === bottom ? [bottom - 1, top + 1] : [bottom, top])
    .range([HEIGHT - STROKE, STROKE]);
  const points = values.map((value, index): [number, number] => [
    x(index),
    y(value),
  ]);
  return (
    <svg
      aria-hidden="true"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
      className="h-8 w-full overflow-visible text-accent"
    >
      <defs>
        <linearGradient id={gradient} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="currentColor" stopOpacity={0.2} />
          <stop offset="1" stopColor="currentColor" stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={area().y0(HEIGHT)(points) ?? ""} fill={`url(#${gradient})`} />
      <path
        d={line()(points) ?? ""}
        fill="none"
        stroke="currentColor"
        strokeWidth={STROKE}
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
