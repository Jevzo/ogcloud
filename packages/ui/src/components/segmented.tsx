import { ToggleGroup } from "radix-ui";
import { cn } from "../lib/cn.ts";

interface SegmentedProps<T extends string> {
  label: string;
  options: readonly { value: T; label: string }[];
  value: T;
  onValueChange: (value: T) => void;
  className?: string;
}

/** A small set of exclusive views, such as a chart's time range. */
export function Segmented<T extends string>({
  label,
  options,
  value,
  onValueChange,
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
      className={cn(
        "inline-flex w-fit gap-0.5 rounded-control bg-surface-3 p-0.5",
        className,
      )}
    >
      {options.map((option) => (
        <ToggleGroup.Item
          key={option.value}
          value={option.value}
          className="h-6 cursor-pointer rounded-[calc(var(--radius-control)-2px)] px-2.5 font-label text-meta text-text-2 transition-colors hover:text-text data-[state=on]:bg-surface data-[state=on]:text-text data-[state=on]:shadow-raised"
        >
          {option.label}
        </ToggleGroup.Item>
      ))}
    </ToggleGroup.Root>
  );
}
