import type { QueryClient } from "@tanstack/react-query";

/** Marks every cached read under these API paths stale, after a change or a live event (D-0105). */
export function invalidatePaths(
  client: QueryClient,
  paths: readonly string[],
): Promise<void> {
  return client.invalidateQueries({
    predicate: (query) =>
      paths.some((path) => String(query.queryKey[0]).startsWith(path)),
  });
}
