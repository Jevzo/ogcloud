import { AnimatePresence, motion } from "motion/react";
import { useTranslation } from "react-i18next";
import { exitTween } from "../lib/motion.ts";
import { Button } from "./button.tsx";

interface SaveBarProps {
  changes: number;
  saving?: boolean;
  onDiscard: () => void;
  onSave: () => void;
}

/** Appears while a page has unsaved edits; it sticks to the bottom of the page's content (D-0210). */
export function SaveBar({
  changes,
  saving = false,
  onDiscard,
  onSave,
}: SaveBarProps) {
  const { t } = useTranslation("ui");
  return (
    <AnimatePresence>
      {changes > 0 ? (
        <motion.div
          role="region"
          aria-label={t("saveBar.changes", { count: changes })}
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 24, transition: exitTween }}
          className="sticky bottom-4 z-30 flex h-14 items-center gap-3 rounded-popover border border-border bg-floating pr-3 pl-4 shadow-popover"
        >
          <span aria-hidden="true" className="size-2 rounded-full bg-warning" />
          <span className="flex-1 font-label">
            {t("saveBar.changes", { count: changes })}
          </span>
          <Button kind="secondary" onClick={onDiscard} disabled={saving}>
            {t("saveBar.discard")}
          </Button>
          <Button kind="primary" onClick={onSave} loading={saving}>
            {t("saveBar.save")}
          </Button>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
