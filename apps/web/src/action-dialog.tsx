import { Button, Dialog } from "@ogcloud/ui";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

interface ActionDialogProps {
  open: boolean;
  /** Also what Cancel calls, with false. */
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  className?: string;
  submitLabel: string;
  onSubmit?: () => void;
  /** Submits this form instead of calling onSubmit. */
  form?: string;
  disabled?: boolean;
  busy: boolean;
  kind?: "primary" | "danger";
  children: ReactNode;
}

/** A dialog that takes some input and then acts: Cancel, then the action itself, last (D-0210). */
export function ActionDialog({
  open,
  onOpenChange,
  title,
  description,
  className,
  submitLabel,
  onSubmit,
  form,
  disabled = false,
  busy,
  kind = "primary",
  children,
}: ActionDialogProps) {
  const { t } = useTranslation("ui");
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      className={className}
      footer={
        <>
          <Button kind="secondary" onClick={() => onOpenChange(false)}>
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
      }
    >
      {children}
    </Dialog>
  );
}
