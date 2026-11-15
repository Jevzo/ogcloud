import { X } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "./button.tsx";

interface FilterBarProps {
  /** The list's controls: search, state switches, selects, dates. */
  children: ReactNode;
  /** Resets every filter; the button shows only while one is set. */
  onClear?: (() => void) | undefined;
}

/** Narrowing a list in one row above it: every filter in view, and a way back to the whole list (D-0235). */
export function FilterBar({ children, onClear }: FilterBarProps) {
  const { t } = useTranslation("ui");
  return (
    <div className="flex flex-wrap items-center gap-2">
      {children}
      {onClear === undefined ? null : (
        <Button kind="ghost" icon={X} onClick={onClear}>
          {t("filters.clear")}
        </Button>
      )}
    </div>
  );
}
