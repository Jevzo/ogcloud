import { Button, cn } from "@ogcloud/ui";
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
      {/* A button, not a field: it opens the palette, where the search field is (D-0235). */}
      <Button icon={Search} onClick={onSearch} aria-keyshortcuts="Meta+K">
        {t("shell.search")}
        <kbd className="rounded-small border border-border bg-surface-2 px-1.5 font-sans text-meta text-text-3">
          ⌘K
        </kbd>
      </Button>
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
