import { cva } from "class-variance-authority";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "../lib/cn.ts";

export type Tone =
  | "neutral"
  | "accent"
  | "success"
  | "warning"
  | "danger"
  | "info";

const badge = cva(
  "inline-flex h-5.5 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-2 font-label text-meta",
  {
    variants: {
      tone: {
        neutral: "bg-surface-3 text-text-2",
        accent: "bg-accent-soft text-accent-text",
        success: "bg-success-soft text-success-text",
        warning: "bg-warning-soft text-warning-text",
        danger: "bg-danger-soft text-danger-text",
        info: "bg-info-soft text-info-text",
      },
    },
  },
);

interface BadgeProps {
  tone: Tone;
  /** States never rely on colour alone: a badge carries an icon, or a dot when no icon fits. */
  icon?: LucideIcon;
  children: ReactNode;
  className?: string;
}

export function Badge({ tone, icon: Icon, children, className }: BadgeProps) {
  return (
    <span data-skeleton="" className={cn(badge({ tone }), className)}>
      {Icon ? (
        <Icon aria-hidden="true" size={13} strokeWidth={2} />
      ) : (
        <span aria-hidden="true" className="size-1.5 rounded-full bg-current" />
      )}
      {children}
    </span>
  );
}
