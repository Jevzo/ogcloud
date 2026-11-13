import { cn } from "@ogcloud/ui";
import { Link, useLocation } from "@tanstack/react-router";
import { ChevronRight, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { LiveState } from "../live.ts";
import { navItemOf } from "../navigation.ts";

const LIVE_DOT: Record<LiveState, string> = {
  connected: "bg-success",
  connecting: "bg-info animate-pulse",
  reconnecting: "bg-warning animate-pulse",
};

export function Header({
  live,
  onSearch,
}: {
  live: LiveState;
  onSearch: () => void;
}) {
  const { t } = useTranslation("web");
  const { pathname } = useLocation();
  const section = navItemOf(pathname);
  return (
    <header className="glass sticky top-0 z-20 flex h-header items-center gap-3 border-border-subtle border-b px-4 [--og-glass-ground:var(--og-page)]">
      {/* Detail and create pages lead back to their list; the page's own title names where one is (D-0231). */}
      <nav aria-label={t("shell.breadcrumbs")} className="min-w-0 flex-1">
        {section === undefined || section.path === pathname ? null : (
          <Link
            to={section.path}
            className="inline-flex h-control items-center gap-1 rounded-control px-1.5 font-label text-text-2 hover:bg-text/7 hover:text-text"
          >
            <section.icon aria-hidden="true" size={16} strokeWidth={1.5} />
            {t(`nav.${section.label}`)}
            <ChevronRight
              aria-hidden="true"
              size={14}
              strokeWidth={1.5}
              className="text-text-3"
            />
          </Link>
        )}
      </nav>
      <button
        type="button"
        onClick={onSearch}
        className="flex h-control w-full max-w-sm cursor-pointer items-center gap-2 rounded-control border border-border bg-surface pr-2 pl-2.5 text-left text-small text-text-3 shadow-raised hover:border-border-strong"
      >
        <Search aria-hidden="true" size={15} strokeWidth={1.75} />
        <span className="flex-1">{t("shell.search")}</span>
        <kbd className="rounded-small border border-border bg-surface-2 px-1.5 font-sans text-meta">
          ⌘K
        </kbd>
      </button>
      <span
        role="status"
        className="inline-flex items-center gap-1.5 text-small text-text-2"
      >
        <span
          aria-hidden="true"
          className={cn("size-2 rounded-full", LIVE_DOT[live])}
        />
        {t(`shell.live.${live}`)}
      </span>
    </header>
  );
}
