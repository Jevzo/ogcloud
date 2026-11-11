import { cn } from "@ogcloud/ui";
import { Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { LiveState } from "../live.ts";

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
  return (
    <header className="sticky top-0 z-20 flex h-header items-center gap-3 bg-sunken/85 px-4 backdrop-blur-sm">
      <button
        type="button"
        onClick={onSearch}
        className="flex h-control w-full max-w-md cursor-pointer items-center gap-2 rounded-control border border-border bg-surface pr-2 pl-2.5 text-left text-small text-text-3 shadow-raised hover:border-border-strong"
      >
        <Search aria-hidden="true" size={15} strokeWidth={1.75} />
        <span className="flex-1">{t("shell.search")}</span>
        <kbd className="rounded-small border border-border bg-surface-2 px-1.5 font-sans text-meta">
          ⌘K
        </kbd>
      </button>
      <span className="flex-1" />
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
