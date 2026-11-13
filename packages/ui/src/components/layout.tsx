import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "../lib/cn.ts";
import type { Tone } from "./badge.tsx";

interface PageHeaderProps {
  /** The page's name, or the subject's: a detail page wraps it in `Value`, so it loads as a skeleton. */
  title: ReactNode;
  /** The subject's state beside its name, such as a server's Running. */
  badges?: ReactNode;
  /** The page's actions, right-aligned with the primary one last (D-0210). */
  actions?: ReactNode;
}

/** A page's title, state and actions; no subtitle, since the page itself says what it holds (D-0228). */
export function PageHeader({ title, badges, actions }: PageHeaderProps) {
  return (
    <div className="flex min-h-button-lg flex-wrap items-center gap-3">
      <div className="flex min-w-60 flex-1 items-center gap-3">
        <h1 className="text-title">{title}</h1>
        {badges}
      </div>
      {actions === undefined ? null : (
        <div className="flex items-center gap-2">{actions}</div>
      )}
    </div>
  );
}

interface CardProps {
  title?: ReactNode;
  /** Small actions or links on the right of the title. */
  actions?: ReactNode;
  /** Tables reach the card's edges, clipped to its corners; everything else gets the card's padding. */
  flush?: boolean;
  className?: string;
  children: ReactNode;
}

export function Card({
  title,
  actions,
  flush = false,
  className,
  children,
}: CardProps) {
  const header = title !== undefined || actions !== undefined;
  return (
    <section
      className={cn(
        "min-w-0 rounded-card border border-border-subtle bg-surface shadow-card",
        // A flush card's title starts as far down as any other card's, so titles side by side line up.
        flush ? cn("overflow-hidden pb-3", header ? "pt-4" : "pt-0") : "p-4",
        className,
      )}
    >
      {!header ? null : (
        <div className={cn("mb-3 flex items-center gap-2", flush && "px-4")}>
          <h2 className="flex-1 text-heading">{title}</h2>
          {/* Small buttons and switches overhang the title's line, so every card's header is as tall as its title. */}
          {actions === undefined ? null : (
            <div className="-my-1 flex items-center gap-2">{actions}</div>
          )}
        </div>
      )}
      {children}
    </section>
  );
}

interface EmptyStateProps {
  icon: LucideIcon;
  title: ReactNode;
  description: ReactNode;
  action?: ReactNode;
}

/** An empty list says what belongs there and offers the first step. */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: EmptyStateProps) {
  return (
    <div className="grain flex flex-col items-center gap-2 rounded-card border border-border-strong border-dashed px-5 py-7 text-center">
      <span
        aria-hidden="true"
        className="grid size-11 place-items-center rounded-full border border-border-subtle bg-surface text-text-2 shadow-raised"
      >
        <Icon size={22} strokeWidth={1.75} />
      </span>
      <span className="text-heading">{title}</span>
      <span className="max-w-sm text-text-2">{description}</span>
      {action === undefined ? null : <div className="mt-1.5">{action}</div>}
    </div>
  );
}

const NOTE_TONES: Record<Exclude<Tone, "neutral" | "highlight">, string> = {
  success: "bg-success-soft [&>svg]:text-success",
  warning: "bg-warning-soft [&>svg]:text-warning",
  danger: "bg-danger-soft [&>svg]:text-danger",
  info: "bg-info-soft [&>svg]:text-info",
};

interface NoteProps {
  tone: keyof typeof NOTE_TONES;
  icon: LucideIcon;
  children: ReactNode;
  action?: ReactNode;
}

/** A message inside a page, such as the warning before changing a running STATIC group's template (D-0123). */
export function Note({ tone, icon: Icon, children, action }: NoteProps) {
  return (
    <div
      role="note"
      className={cn(
        "flex gap-2.5 rounded-control px-3.5 py-3",
        NOTE_TONES[tone],
      )}
    >
      <Icon
        aria-hidden="true"
        size={16}
        strokeWidth={1.75}
        className="mt-0.5 shrink-0"
      />
      <div className="min-w-0 flex-1">{children}</div>
      {action === undefined ? null : (
        <div className="self-center">{action}</div>
      )}
    </div>
  );
}
