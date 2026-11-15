import { Select } from "@ogcloud/ui";
import { type ReactNode, useState } from "react";
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
      className="w-48"
      aria-label={t("servers.filters.group")}
      options={[
        { value: ALL_GROUPS, label: t("servers.filters.allGroups") },
        ...groupIds.map((id) => ({ value: id, label: id })),
      ]}
      value={value}
      onValueChange={onValueChange}
    />
  );
}

/** A list's group filter in its filter bar: the chosen group, its control, and whether it narrows the list. */
export function useGroupFilter(groupIds: readonly string[]): {
  group: string;
  control: ReactNode;
  active: boolean;
  clear: () => void;
} {
  const [group, setGroup] = useState(ALL_GROUPS);
  return {
    group,
    control: (
      <GroupFilter groupIds={groupIds} value={group} onValueChange={setGroup} />
    ),
    active: group !== ALL_GROUPS,
    clear: () => setGroup(ALL_GROUPS),
  };
}
