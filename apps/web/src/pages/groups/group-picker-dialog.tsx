import { useState } from "react";
import { ActionDialog } from "../../action-dialog.tsx";
import { GroupSelectField } from "./group-select-field.tsx";

interface GroupPickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  fieldLabel: string;
  submitLabel: string;
  groupIds: readonly string[];
  busy: boolean;
  onPick: (groupId: string) => void;
}

/** A dialog whose one question is a group, such as where a new server or moved players go. */
export function GroupPickerDialog({
  open,
  onOpenChange,
  title,
  description,
  fieldLabel,
  submitLabel,
  groupIds,
  busy,
  onPick,
}: GroupPickerDialogProps) {
  const [group, setGroup] = useState<string>();

  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      submitLabel={submitLabel}
      onSubmit={() => (group === undefined ? undefined : onPick(group))}
      disabled={group === undefined}
      busy={busy}
    >
      <GroupSelectField
        label={fieldLabel}
        groupIds={groupIds}
        value={group}
        onValueChange={setGroup}
      />
    </ActionDialog>
  );
}
