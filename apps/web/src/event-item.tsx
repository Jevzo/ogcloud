import { cn } from "@ogcloud/ui";
import type { LucideIcon } from "lucide-react";
import { Ago } from "./ago.tsx";

/** What happened, in a short list of recent events: its icon on a tint, what and why, and when. */
export function EventItem({
  icon: Icon,
  tint,
  title,
  detail,
  at,
}: {
  icon: LucideIcon;
  /** The icon's background and colour, such as `bg-success-soft text-success`. */
  tint: string;
  title: string;
  detail: string;
  at: string;
}) {
  return (
    <li className="flex items-center gap-3 py-1.5">
      <span
        aria-hidden="true"
        data-skeleton=""
        className={cn(
          "grid size-7 shrink-0 place-items-center rounded-full",
          tint,
        )}
      >
        <Icon size={14} strokeWidth={2} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span data-skeleton="" className="truncate" title={title}>
          {title}
        </span>
        <span
          data-skeleton=""
          className="truncate text-meta text-text-3"
          title={detail}
        >
          {detail}
        </span>
      </span>
      <Ago at={at} className="shrink-0 text-meta text-text-3" />
    </li>
  );
}

/** The way from a card of recent events to the Activity page. */
export const ACTIVITY_LINK =
  "inline-flex min-h-6 items-center font-label text-accent text-small hover:underline";
