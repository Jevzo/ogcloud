import { type ListFilterSpec, Select } from "@ogcloud/ui";
import { useState } from "react";
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
      className="w-full"
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

/** A list's group filter for its filter bar: the chosen group, and the filter that sets and shows it (D-0231). */
export function useGroupFilter(groupIds: readonly string[]): {
  group: string;
  filter: ListFilterSpec;
} {
  const { t } = useTranslation("web");
  const [group, setGroup] = useState(ALL_GROUPS);
  return {
    group,
    filter: {
      id: "group",
      label: t("servers.filters.group"),
      control: (
        <GroupFilter
          groupIds={groupIds}
          value={group}
          onValueChange={setGroup}
        />
      ),
      shown: group === ALL_GROUPS ? null : group,
      clear: () => setGroup(ALL_GROUPS),
    },
  };
}
