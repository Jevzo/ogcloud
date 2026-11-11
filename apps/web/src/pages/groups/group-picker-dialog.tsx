import { Button, Dialog, Field, Select } from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";

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
  const { t } = useTranslation("web");
  const { t: ui } = useTranslation("ui");
  const [group, setGroup] = useState<string>();

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      footer={
        <>
          <Button kind="secondary" onClick={() => onOpenChange(false)}>
            {ui("actions.cancel")}
          </Button>
          <Button
            kind="primary"
            onClick={() => (group === undefined ? undefined : onPick(group))}
            disabled={group === undefined}
            loading={busy}
          >
            {submitLabel}
          </Button>
        </>
      }
    >
      <Field label={fieldLabel}>
        {(control) => (
          <Select
            {...control}
            options={groupIds.map((id) => ({ value: id, label: id }))}
            value={group}
            onValueChange={setGroup}
            placeholder={t("servers.request.pickGroup")}
          />
        )}
      </Field>
    </Dialog>
  );
}
