import { Button } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";

interface DialogFooterProps {
  onCancel: () => void;
  submitLabel: string;
  onSubmit?: () => void;
  /** Submits this form instead of calling onSubmit. */
  form?: string;
  disabled?: boolean;
  busy: boolean;
  kind?: "primary" | "danger";
}

/** A dialog's actions: Cancel, then the action itself, last (D-0210). */
export function DialogFooter({
  onCancel,
  submitLabel,
  onSubmit,
  form,
  disabled = false,
  busy,
  kind = "primary",
}: DialogFooterProps) {
  const { t } = useTranslation("ui");
  return (
    <>
      <Button kind="secondary" onClick={onCancel}>
        {t("actions.cancel")}
      </Button>
      <Button
        kind={kind}
        {...(form === undefined
          ? { onClick: onSubmit }
          : { type: "submit" as const, form })}
        disabled={disabled}
        loading={busy}
      >
        {submitLabel}
      </Button>
    </>
  );
}
