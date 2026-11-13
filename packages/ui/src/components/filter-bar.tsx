import { ListFilter, X } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "./button.tsx";
import { Popover } from "./popover.tsx";

/** One way to narrow a list: its control in the Filter popover, and a chip while it is set. */
export interface ListFilterSpec {
  id: string;
  label: string;
  /** The control that sets it, such as a select or a text field. */
  control: ReactNode;
  /** What it is set to, as the chip says it; null while it is not set. */
  shown: string | null;
  clear: () => void;
}

interface FilterBarProps {
  /** Controls that stay in view, such as a state switch and the name search. */
  children?: ReactNode;
  filters: readonly ListFilterSpec[];
}

/**
 * Narrowing a list in one row above it: the controls used most stay in view, the rest open from "Filter", and every
 * filter that is set shows as a chip with a way to clear it (D-0231).
 */
export function FilterBar({ children, filters }: FilterBarProps) {
  const { t } = useTranslation("ui");
  const active = filters.filter((filter) => filter.shown !== null);
  return (
    <div className="flex flex-wrap items-center gap-2">
      {children}
      {filters.length === 0 ? null : (
        <Popover
          label={t("filters.title")}
          className="flex w-72 flex-col gap-3 p-3"
          trigger={
            <Button icon={ListFilter}>
              {t("filters.title")}
              {active.length === 0 ? null : (
                <span className="grid min-w-5 place-items-center rounded-full bg-accent px-1 text-meta text-on-accent tabular-nums">
                  {active.length}
                </span>
              )}
            </Button>
          }
        >
          {filters.map((filter) => (
            <div key={filter.id} className="flex flex-col gap-1.5">
              <span className="font-label">{filter.label}</span>
              {filter.control}
            </div>
          ))}
        </Popover>
      )}
      {active.map((filter) => (
        <span
          key={filter.id}
          className="inline-flex h-7 items-center gap-1 rounded-full border border-border bg-surface pr-0.5 pl-2.5 text-small shadow-raised"
        >
          <span className="text-text-3">{filter.label}:</span>
          <span className="max-w-48 truncate font-label">{filter.shown}</span>
          <button
            type="button"
            aria-label={t("filters.remove", { filter: filter.label })}
            onClick={filter.clear}
            className="grid size-6 cursor-pointer place-items-center rounded-full text-text-3 hover:bg-text/7 hover:text-text"
          >
            <X aria-hidden="true" size={13} />
          </button>
        </span>
      ))}
      {active.length < 2 ? null : (
        <Button
          kind="ghost"
          size="sm"
          onClick={() => {
            for (const filter of active) filter.clear();
          }}
        >
          {t("filters.clearAll")}
        </Button>
      )}
    </div>
  );
}
