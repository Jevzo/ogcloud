import { Check, Copy } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Tooltip } from "./tooltip.tsx";

const COPIED_FOR_MS = 1500;

/** An identifier shown in full, in the mono face, with a copy button. */
export function CopyField({ value, label }: { value: string; label: string }) {
  const { t } = useTranslation("ui");
  const [outcome, setOutcome] = useState<"copied" | "failed" | null>(null);
  useEffect(() => {
    if (outcome === null) return;
    const timer = setTimeout(() => setOutcome(null), COPIED_FOR_MS);
    return () => clearTimeout(timer);
  }, [outcome]);

  // The clipboard refuses without permission or focus; the tooltip says so instead of failing silently.
  const copy = () =>
    navigator.clipboard.writeText(value).then(
      () => setOutcome("copied"),
      () => setOutcome("failed"),
    );

  return (
    <span
      data-skeleton=""
      className="flex h-control items-center gap-1 rounded-control border border-border bg-surface-2 pr-1 pl-2.5"
    >
      <span className="flex-1 truncate font-mono text-mono text-text-2">
        {value}
      </span>
      <Tooltip
        content={outcome === null ? t("actions.copy") : t(`actions.${outcome}`)}
      >
        <button
          type="button"
          aria-label={`${t("actions.copy")} ${label}`}
          onClick={copy}
          className="grid size-6.5 cursor-pointer place-items-center rounded-[calc(var(--radius-control)-2px)] text-text-2 hover:bg-surface-3"
        >
          {outcome === "copied" ? (
            <Check aria-hidden="true" size={15} />
          ) : (
            <Copy aria-hidden="true" size={15} strokeWidth={1.75} />
          )}
        </button>
      </Tooltip>
    </span>
  );
}
