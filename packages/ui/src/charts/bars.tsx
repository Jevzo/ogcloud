import type { Tone } from "../components/badge.tsx";
import { cn } from "../lib/cn.ts";

const WARN_AT = 0.9;

interface MeterProps {
  value: number;
  max: number;
  className?: string;
}

/** How full something is, beside the number it shows; it turns to the warning colour from 90 %. */
export function Meter({ value, max, className }: MeterProps) {
  const ratio = max <= 0 ? 0 : Math.min(1, value / max);
  return (
    <span
      aria-hidden="true"
      className={cn(
        "inline-block h-1.5 w-16 overflow-hidden rounded-full bg-surface-3",
        className,
      )}
    >
      <span
        className={cn(
          "block h-full rounded-full",
          ratio >= WARN_AT ? "bg-warning" : "bg-accent",
        )}
        style={{ width: `${ratio * 100}%` }}
      />
    </span>
  );
}

export interface Segment {
  label: string;
  value: number;
  tone: Exclude<Tone, "neutral">;
}

const SEGMENT_FILL: Record<Segment["tone"], string> = {
  accent: "bg-accent",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-danger",
  info: "bg-info",
};

/** Parts of a whole, such as servers by state; the list next to it carries the labels and counts. */
export function StackedBar({
  segments,
  className,
}: {
  segments: readonly Segment[];
  className?: string;
}) {
  const shown = segments.filter((segment) => segment.value > 0);
  return (
    <span
      aria-hidden="true"
      className={cn(
        "flex h-2.5 gap-0.5 overflow-hidden rounded-[4px]",
        className,
      )}
    >
      {shown.map((segment) => (
        <span
          key={segment.label}
          className={SEGMENT_FILL[segment.tone]}
          style={{ flexGrow: segment.value }}
        />
      ))}
    </span>
  );
}
