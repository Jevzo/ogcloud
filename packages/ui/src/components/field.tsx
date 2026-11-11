import { CircleAlert } from "lucide-react";
import { type ReactNode, useId } from "react";
import { cn } from "../lib/cn.ts";

/** The ids a control needs to name its label, help and error. */
export interface FieldControl {
  id: string;
  "aria-describedby": string | undefined;
  "aria-invalid": true | undefined;
}

interface FieldProps {
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  className?: string;
  children: (control: FieldControl) => ReactNode;
}

/** A label, the control, then either its error or its help text, wired for screen readers. */
export function Field({ label, hint, error, className, children }: FieldProps) {
  const id = useId();
  const messageId = `${id}-message`;
  const message = error ?? hint;
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <label htmlFor={id} className="font-label text-body">
        {label}
      </label>
      {children({
        id,
        "aria-describedby": message === undefined ? undefined : messageId,
        "aria-invalid": error === undefined ? undefined : true,
      })}
      {error !== undefined ? (
        <span
          id={messageId}
          className="flex items-center gap-1.5 text-danger-text text-small"
        >
          <CircleAlert aria-hidden="true" size={14} className="shrink-0" />
          {error}
        </span>
      ) : hint !== undefined ? (
        <span id={messageId} className="text-small text-text-3">
          {hint}
        </span>
      ) : null}
    </div>
  );
}
