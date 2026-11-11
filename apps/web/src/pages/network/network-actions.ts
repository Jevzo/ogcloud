import {
  getNetworkGetQueryKey,
  getOverviewGetQueryKey,
  useNetworkGetHook,
  useNetworkUpdateHook,
} from "@ogcloud/api-client/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { invalidatePaths } from "../../queries.ts";

/** Turns network maintenance on or off, writing against the settings' current version (D-0069). */
export function useNetworkMaintenance() {
  const read = useNetworkGetHook();
  const update = useNetworkUpdateHook();
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (maintenance: boolean) => {
      const { data } = await read();
      await update({
        settings: { ...data.settings, maintenance },
        version: data.version,
      });
    },
    onSettled: () =>
      invalidatePaths(client, [
        getNetworkGetQueryKey()[0],
        getOverviewGetQueryKey()[0],
      ]),
  });
}
