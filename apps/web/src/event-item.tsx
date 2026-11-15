import { Card, cn } from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
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

/**
 * Recent events in a card with the way to the Activity page, newest first; [empty] says why there are none, and
 * nothing shows while they load.
 */
export function EventsCard({
  title,
  empty,
  loading = false,
  children,
}: {
  title: string;
  empty: string;
  loading?: boolean;
  /** The events, as [EventItem]s; none shows [empty]. */
  children: readonly ReactNode[];
}) {
  const { t } = useTranslation("web");
  return (
    <Card
      title={title}
      actions={
        <Link
          to="/activity"
          className="inline-flex min-h-6 items-center font-label text-accent text-small hover:underline"
        >
          {t("overview.scaling.all")}
        </Link>
      }
    >
      {children.length === 0 ? (
        <p className="text-small text-text-3">{loading ? "" : empty}</p>
      ) : (
        <ol className="flex flex-col">{children}</ol>
      )}
    </Card>
  );
}
