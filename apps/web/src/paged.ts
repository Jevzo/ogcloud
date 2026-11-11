import { useInfiniteQuery } from "@tanstack/react-query";

/** The API's largest page (D-0101); lists load more as the reader asks. */
export const PAGE_SIZE = 200;

interface Page<T> {
  items: T[];
  nextCursor?: string | null;
}

/** A cursor-paged list, its pages joined; `loadMore` fetches the next page while there is one. */
export function usePaged<T>(
  queryKey: readonly unknown[],
  fetchPage: (cursor: string | undefined) => Promise<Page<T>>,
  refetchInterval?: number,
) {
  const query = useInfiniteQuery({
    queryKey: ["paged", ...queryKey],
    queryFn: ({ pageParam }) => fetchPage(pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    ...(refetchInterval === undefined ? {} : { refetchInterval }),
  });
  return {
    items: query.data?.pages.flatMap((page) => page.items) ?? [],
    pending: query.isPending,
    error: query.error,
    hasMore: query.hasNextPage,
    loadingMore: query.isFetchingNextPage,
    loadMore: () => void query.fetchNextPage(),
  };
}
