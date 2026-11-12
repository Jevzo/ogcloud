import { ToggleGroup } from "radix-ui";
import { cn } from "../lib/cn.ts";

interface SegmentedProps<T extends string> {
  label: string;
  options: readonly { value: T; label: string }[];
  value: T;
  onValueChange: (value: T) => void;
  /** md stands as tall as the inputs beside it; sm fits a card's header, like a small button. */
  size?: "sm" | "md";
  className?: string;
}

const HEIGHTS = { sm: "h-button-sm", md: "h-control" } as const;

/** A small set of exclusive views, such as a chart's time range. */
export function Segmented<T extends string>({
  label,
  options,
  value,
  onValueChange,
  size = "md",
  className,
}: SegmentedProps<T>) {
  return (
    <ToggleGroup.Root
      type="single"
      aria-label={label}
      value={value}
      onValueChange={(next) =>
        next === "" ? undefined : onValueChange(next as T)
      }
      // The border keeps the track visible on the page background, which surface 3 nearly matches in light mode.
      className={cn(
        "inline-flex w-fit shrink-0 items-stretch gap-0.5 rounded-control border border-border bg-surface-3 p-0.5",
        HEIGHTS[size],
        className,
      )}
    >
      {options.map((option) => (
        <ToggleGroup.Item
          key={option.value}
          value={option.value}
          className="cursor-pointer rounded-[calc(var(--radius-control)-2px)] px-2.5 font-label text-meta text-text-2 hover:text-text data-[state=on]:bg-surface data-[state=on]:text-text data-[state=on]:shadow-raised"
        >
          {option.label}
        </ToggleGroup.Item>
      ))}
    </ToggleGroup.Root>
  );
}
