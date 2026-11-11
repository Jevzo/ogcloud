import { ConfirmDialog, type MenuItem, useToast } from "@ogcloud/ui";
import { Trash2 } from "lucide-react";
import { type ReactNode, useState } from "react";
import { useFailureToast } from "./errors.ts";

interface DeleteActionOptions {
  label: string;
  title: string;
  description: string;
  /** What must be typed first, since deleting cannot be undone (D-0210). */
  typedName: string;
  /** The toast once it is gone. */
  done: string;
  remove: () => Promise<unknown>;
  onDeleted: () => void;
}

/** A detail page's delete, behind its ⋯ menu: a typed confirmation, a toast, then usually the way back to the list. */
export function useDeleteAction({
  label,
  title,
  description,
  typedName,
  done,
  remove,
  onDeleted,
}: DeleteActionOptions): { item: MenuItem; dialog: ReactNode } {
  const toast = useToast();
  const failed = useFailureToast();
  const [open, setOpen] = useState(false);
  return {
    item: { label, icon: Trash2, danger: true, onSelect: () => setOpen(true) },
    dialog: (
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title={title}
        description={description}
        confirmLabel={label}
        typedName={typedName}
        onConfirm={() =>
          remove().then(() => {
            toast({ message: done });
            onDeleted();
          }, failed)
        }
      />
    ),
  };
}
