import { useOverviewGet } from "@ogcloud/api-client/react";

/** The groups players can be sent to and NPCs can stand in: every group but proxies. */
export function useBackendGroupIds(): string[] {
  const overview = useOverviewGet();
  return (overview.data?.data.groups ?? [])
    .filter((group) => group.type !== "PROXY")
    .map((group) => group.id);
}
