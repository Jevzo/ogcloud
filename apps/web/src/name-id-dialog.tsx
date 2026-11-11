import { Field, Input, useToast } from "@ogcloud/ui";
import { useState } from "react";
import { ActionDialog, useOnOpen } from "./action-dialog.tsx";
import { useFailureToast } from "./errors.ts";

interface NameIdDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  submitLabel: string;
  name: { label: string; placeholder: string };
  /** `schema` is the contract's rule for the id, as orval generates it (D-0011). */
  id: {
    label: string;
    hint: string;
    placeholder: string;
    schema: { safeParse: (value: string) => { success: boolean } };
  };
  create: (id: string, name: string) => Promise<unknown>;
  /** The toast once it exists. */
  done: (name: string) => string;
  /** Usually opens the new thing's page, where the rest is set. */
  onCreated: (id: string) => void;
}

/** Creates something that needs only a name and a fixed id; the rest is set on its page (D-0210). */
export function NameIdDialog({
  open,
  onOpenChange,
  title,
  description,
  submitLabel,
  name: nameField,
  id: idField,
  create,
  done,
  onCreated,
}: NameIdDialogProps) {
  const toast = useToast();
  const failed = useFailureToast();
  const [id, setId] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  useOnOpen(open, () => {
    setId("");
    setName("");
  });
  const submit = () => {
    const trimmed = name.trim();
    setBusy(true);
    create(id, trimmed)
      .then(() => {
        toast({ message: done(trimmed) });
        onOpenChange(false);
        onCreated(id);
      }, failed)
      .finally(() => setBusy(false));
  };
  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      submitLabel={submitLabel}
      onSubmit={submit}
      disabled={!idField.schema.safeParse(id).success || name.trim() === ""}
      busy={busy}
    >
      <Field label={nameField.label}>
        {(control) => (
          <Input
            {...control}
            value={name}
            placeholder={nameField.placeholder}
            onChange={(event) => setName(event.target.value)}
          />
        )}
      </Field>
      <Field label={idField.label} hint={idField.hint}>
        {(control) => (
          <Input
            {...control}
            mono
            value={id}
            placeholder={idField.placeholder}
            onChange={(event) => setId(event.target.value)}
          />
        )}
      </Field>
    </ActionDialog>
  );
}
