import { motion } from "@ogcloud/tokens";
import { ArrowDownRight, ArrowRight, ArrowUpRight } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Sparkline } from "../charts/sparkline.tsx";
import { cn } from "../lib/cn.ts";
import { useLoading } from "./loading.tsx";

/** How a KPI moved against its baseline; a tile never shows a bare number without one. */
export interface KpiDelta {
  /** The change with its sign, already formatted: "+12". */
  change: string;
  /** What it compares with: "vs 1 h ago". */
  basis: string;
  trend: "up" | "down" | "flat";
  /** Whether the move is good news; left out, the chip stays neutral. */
  good?: boolean;
}

interface KpiTileProps {
  label: string;
  value: number;
  format: (value: number) => string;
  /** A short fact after the value, such as "of 5,000 slots", or the delta when there is no history. */
  caption?: string | undefined;
  delta?: KpiDelta | undefined;
  /** Recent values, oldest first, drawn as a sparkline under the value. */
  history?: readonly number[] | undefined;
  /** Warning colours for a value that needs a look, such as paused groups. */
  warn?: boolean;
  className?: string;
}

const TREND_ICONS = {
  up: ArrowUpRight,
  down: ArrowDownRight,
  flat: ArrowRight,
} as const;

/** The number a screen is for: a muted label, the value large and tabular, its baseline, its recent shape (D-0231). */
export function KpiTile({
  label,
  value,
  format,
  caption,
  delta,
  history,
  warn = false,
  className,
}: KpiTileProps) {
  const loading = useLoading();
  const shown = useCountUp(value, !loading);
  const flashes = useFlashes(value, !loading);
  const TrendIcon = delta === undefined ? null : TREND_ICONS[delta.trend];
  return (
    <section
      className={cn(
        "flex min-w-0 flex-col gap-1.5 rounded-card border border-border-subtle bg-surface p-4 shadow-card",
        warn && "border-warning/40 bg-warning-soft",
        className,
      )}
    >
      <h2 className="font-label text-small text-text-2">{label}</h2>
      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <span
          data-skeleton=""
          className={cn(
            "relative isolate text-kpi tabular-nums",
            warn && "text-warning",
          )}
        >
          {format(shown)}
          {flashes === 0 ? null : (
            // A changed value glows in the accent tint behind it for a second.
            <span
              key={flashes}
              aria-hidden="true"
              className="pointer-events-none absolute -inset-x-1.5 inset-y-0 -z-10 animate-flash rounded-small bg-accent-soft opacity-0"
            />
          )}
        </span>
        {caption === undefined ? null : (
          <span data-skeleton="" className="text-small text-text-3">
            {caption}
          </span>
        )}
      </div>
      {delta === undefined || TrendIcon === null ? null : (
        <span className="flex items-center gap-1.5 text-meta text-text-3">
          <span
            data-skeleton=""
            className={cn(
              "inline-flex h-5 items-center gap-0.5 rounded-full px-1.5 font-label tabular-nums",
              delta.good === undefined
                ? "bg-surface-3 text-text-2"
                : delta.good
                  ? "bg-success-soft text-success"
                  : "bg-danger-soft text-danger",
            )}
          >
            <TrendIcon aria-hidden="true" size={12} strokeWidth={2} />
            {delta.change}
          </span>
          {delta.basis}
        </span>
      )}
      {history === undefined ? null : (
        <div data-skeleton="" className="mt-1">
          <Sparkline values={history} />
        </div>
      )}
    </section>
  );
}

/** Counts up to the first value once, then follows it; still under reduced motion. */
function useCountUp(target: number, ready: boolean): number {
  const [shown, setShown] = useState(target);
  const counted = useRef(false);
  useEffect(() => {
    if (!ready || counted.current) {
      setShown(target);
      return;
    }
    counted.current = true;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setShown(target);
      return;
    }
    const start = performance.now();
    let frame = 0;
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / motion.countUpMs);
      // Ease out, so the count settles instead of stopping hard.
      setShown(Math.round(target * (1 - (1 - progress) ** 3)));
      if (progress < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target, ready]);
  return shown;
}

/** How often the value changed after it first loaded; each change replays the accent flash. */
function useFlashes(value: number, ready: boolean): number {
  const seen = useRef<number | undefined>(undefined);
  const [flashes, setFlashes] = useState(0);
  useEffect(() => {
    if (!ready) return;
    if (seen.current !== undefined && seen.current !== value) {
      setFlashes((count) => count + 1);
    }
    seen.current = value;
  }, [value, ready]);
  return flashes;
}
