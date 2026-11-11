import { useServerList } from "@ogcloud/api-client/react";
import { useCan } from "../../permissions.ts";

/** Live servers' short names by id, for showing where players are; empty without `servers:read`. */
export function useServerNames(): ReadonlyMap<string, string> {
  const servers = useServerList(undefined, {
    query: { enabled: useCan("servers:read") },
  });
  return new Map(
    (servers.data?.data ?? []).map((server) => [server.id, server.displayName]),
  );
}
