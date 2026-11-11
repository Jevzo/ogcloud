import { Select } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";

// No group id can be "*" (they are DNS labels), so it stands for every group.
export const ALL_GROUPS = "*";

/** Narrows a list to one group, or shows every group. */
export function GroupFilter({
  groupIds,
  value,
  onValueChange,
}: {
  groupIds: readonly string[];
  value: string;
  onValueChange: (groupId: string) => void;
}) {
  const { t } = useTranslation("web");
  return (
    <Select
      className="w-56"
      aria-describedby={undefined}
      options={[
        { value: ALL_GROUPS, label: t("servers.filters.allGroups") },
        ...groupIds.map((id) => ({ value: id, label: id })),
      ]}
      value={value}
      onValueChange={onValueChange}
    />
  );
}
