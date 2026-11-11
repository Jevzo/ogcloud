import { useRoleList } from "@ogcloud/api-client/react";
import { useCan } from "../../permissions.ts";

/** Role names by id for readers of roles; without `roles:read` the ids stand in. */
export function useRoleNames(): {
  nameOf: (roleId: string) => string;
  options: { value: string; label: string }[];
} {
  const canRead = useCan("roles:read");
  const roles = useRoleList({ query: { enabled: canRead } });
  const list = roles.data?.data ?? [];
  return {
    nameOf: (roleId) => list.find((role) => role.id === roleId)?.name ?? roleId,
    options: list.map((role) => ({ value: role.id, label: role.name })),
  };
}
