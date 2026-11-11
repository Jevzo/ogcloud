import type { ComponentProps, ReactNode } from "react";
import { cn } from "../lib/cn.ts";

/** The outline and focus every text control shares, invalid ones in the danger colour. */
export const controlFrame =
  "rounded-control border border-border-control bg-surface text-text transition-[border-color,box-shadow] focus-within:border-accent focus-within:ring-3 focus-within:ring-accent/30 aria-invalid:border-danger-text aria-invalid:ring-3 aria-invalid:ring-danger-soft has-[[aria-invalid=true]]:border-danger-text has-[[aria-invalid=true]]:ring-3 has-[[aria-invalid=true]]:ring-danger-soft";

interface InputProps extends ComponentProps<"input"> {
  /** A unit or short text after the value, e.g. MiB. */
  suffix?: ReactNode;
  mono?: boolean;
}

export function Input({
  suffix,
  mono = false,
  className,
  ...props
}: InputProps) {
  return (
    <span
      className={cn(
        controlFrame,
        "flex h-control items-center overflow-hidden",
        className,
      )}
    >
      <input
        {...props}
        className={cn(
          "h-full min-w-0 flex-1 bg-transparent px-2.5 outline-none placeholder:text-text-3 disabled:text-text-disabled",
          mono ? "font-mono text-mono" : "text-body",
        )}
      />
      {suffix === undefined ? null : (
        <span className="grid h-full place-items-center border-border border-l px-2.5 text-small text-text-3">
          {suffix}
        </span>
      )}
    </span>
  );
}
