import type { Tone } from "../components/badge.tsx";
import { Tooltip } from "../components/tooltip.tsx";
import { cn } from "../lib/cn.ts";

const WARN_AT = 0.9;

interface MeterProps {
  value: number;
  max: number;
  /** A capacity warns from 90 %; progress, which should reach 100 %, does not. */
  kind?: "capacity" | "progress";
  className?: string;
}

/** How full something is, beside the number it shows; it turns to the warning colour from 90 %. */
export function Meter({
  value,
  max,
  kind = "capacity",
  className,
}: MeterProps) {
  const ratio = max <= 0 ? 0 : Math.min(1, value / max);
  return (
    <span
      aria-hidden="true"
      data-skeleton=""
      className={cn(
        "inline-block h-1.5 w-16 overflow-hidden rounded-full bg-surface-3",
        className,
      )}
    >
      <span
        className={cn(
          "block h-full rounded-full",
          kind === "capacity" && ratio >= WARN_AT ? "bg-warning" : "bg-accent",
        )}
        style={{ width: `${ratio * 100}%` }}
      />
    </span>
  );
}

/** One part of a whole, such as the groups in one state. */
export interface Segment {
  label: string;
  value: number;
  tone: Exclude<Tone, "highlight">;
}

/** Status fills; idle and other neutral parts take the muted text colour, which keeps 3:1 on the surface. */
export const SEGMENT_FILL: Record<Segment["tone"], string> = {
  neutral: "bg-text-3",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-danger",
  info: "bg-info",
};

/**
 * Parts of a whole side by side in one joined line, rounded only at its ends, each part as wide as its share and named
 * in a tooltip; a legend beside it says the same.
 */
export function StackedBar({
  segments,
  format,
  className,
}: {
  segments: readonly Segment[];
  format: (value: number) => string;
  className?: string;
}) {
  return (
    <span
      data-skeleton=""
      className={cn(
        "flex h-2.5 w-full overflow-hidden rounded-full",
        className,
      )}
    >
      {segments
        .filter((segment) => segment.value > 0)
        .map((segment) => (
          <Tooltip
            key={segment.label}
            content={`${format(segment.value)} ${segment.label}`}
          >
            <span
              aria-hidden="true"
              className={cn("h-full", SEGMENT_FILL[segment.tone])}
              style={{ flexGrow: segment.value }}
            />
          </Tooltip>
        ))}
    </span>
  );
}
