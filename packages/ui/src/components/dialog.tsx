import { Dialog as Primitive } from "radix-ui";
import { type ReactNode, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "../lib/cn.ts";
import { Button } from "./button.tsx";
import { Input } from "./input.tsx";

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  /** The actions, right-aligned with the primary one last (D-0210). */
  footer: ReactNode;
  className?: string;
}

export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  className,
}: DialogProps) {
  return (
    <Primitive.Root open={open} onOpenChange={onOpenChange}>
      <Primitive.Portal>
        <Primitive.Overlay className="fixed inset-0 z-40 bg-scrim data-[state=closed]:animate-fade-out data-[state=open]:animate-fade-in" />
        <Primitive.Content
          className={cn(
            "fixed top-1/2 left-1/2 z-50 flex w-[min(30rem,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 flex-col gap-3 rounded-popover border border-border bg-surface p-5 shadow-popover outline-none data-[state=closed]:animate-pop-out data-[state=open]:animate-pop-in",
            className,
          )}
        >
          <Primitive.Title className="text-title">{title}</Primitive.Title>
          {description === undefined ? null : (
            <Primitive.Description className="text-text-2">
              {description}
            </Primitive.Description>
          )}
          {children}
          <div className="flex justify-end gap-2 pt-2">{footer}</div>
        </Primitive.Content>
      </Primitive.Portal>
    </Primitive.Root>
  );
}

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  /** What happens, in the reader's terms. */
  description: ReactNode;
  confirmLabel: string;
  /** Irreversible or network-wide: a danger button, and the target's name must be typed (D-0210). */
  typedName?: string;
  onConfirm: () => Promise<void> | void;
}

/** Confirms a destructive action; the dialog stays open, busy, until the action has finished. */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  typedName,
  onConfirm,
}: ConfirmDialogProps) {
  const { t } = useTranslation("ui");
  const inputId = useId();
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const ready = typedName === undefined || typed === typedName;

  const close = (next: boolean) => {
    if (!next) setTyped("");
    onOpenChange(next);
  };
  const confirm = async () => {
    setBusy(true);
    try {
      await onConfirm();
      close(false);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={close}
      title={title}
      description={description}
      footer={
        <>
          <Button kind="secondary" onClick={() => close(false)} disabled={busy}>
            {t("actions.cancel")}
          </Button>
          <Button
            kind={typedName === undefined ? "primary" : "danger"}
            onClick={confirm}
            disabled={!ready}
            loading={busy}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {typedName === undefined ? null : (
        <div className="flex flex-col gap-1.5 pt-1">
          <label htmlFor={inputId} className="font-label">
            {t("confirm.typeName", { name: typedName })}
          </label>
          <Input
            id={inputId}
            mono
            autoComplete="off"
            spellCheck={false}
            value={typed}
            onChange={(event) => setTyped(event.target.value)}
          />
        </div>
      )}
    </Dialog>
  );
}
