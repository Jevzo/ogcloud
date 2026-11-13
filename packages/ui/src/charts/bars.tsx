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
