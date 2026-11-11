import { useInfiniteQuery } from "@tanstack/react-query";

/** The API's largest page (D-0101); lists load more as the reader asks. */
export const PAGE_SIZE = 200;

interface Page<T> {
  items: T[];
  nextCursor?: string | null;
}

/**
 * A cursor-paged list, its pages joined; `loadMore` fetches the next page while there is one. `refetchInterval`
 * refreshes only while one page is loaded: a refresh refetches every loaded page, so a list read further stays put.
 */
export function usePaged<T>(
  queryKey: readonly unknown[],
  fetchPage: (cursor: string | undefined) => Promise<Page<T>>,
  refetchInterval?: number,
) {
  const query = useInfiniteQuery({
    // The path stays first, so invalidatePaths reaches the pages too; the marker keeps them apart from plain reads.
    queryKey: [...queryKey, "paged"],
    queryFn: ({ pageParam }) => fetchPage(pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    refetchInterval: (current) =>
      refetchInterval === undefined ||
      (current.state.data?.pages.length ?? 0) > 1
        ? false
        : refetchInterval,
  });
  return {
    items: query.data?.pages.flatMap((page) => page.items) ?? [],
    pending: query.isPending,
    error: query.error,
    hasMore: query.hasNextPage,
    loadingMore: query.isFetchingNextPage,
    loadMore: () => void query.fetchNextPage(),
    refetch: () => void query.refetch(),
  };
}
