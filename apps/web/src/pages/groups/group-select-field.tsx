import { Field, Select } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";

interface GroupSelectFieldProps {
  label: string;
  groupIds: readonly string[];
  value: string | undefined;
  onValueChange: (groupId: string) => void;
}

export function GroupSelectField({
  label,
  groupIds,
  value,
  onValueChange,
}: GroupSelectFieldProps) {
  const { t } = useTranslation("web");
  return (
    <Field label={label}>
      {(control) => (
        <Select
          {...control}
          options={groupIds.map((id) => ({ value: id, label: id }))}
          value={value}
          onValueChange={onValueChange}
          placeholder={t("servers.request.pickGroup")}
        />
      )}
    </Field>
  );
}
