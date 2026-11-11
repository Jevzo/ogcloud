import { Button } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";
import type { usePaged } from "./paged.ts";

/** Fetches the next page of a long list while there is one. */
export function LoadMore({
  paged,
}: {
  paged: Pick<
    ReturnType<typeof usePaged>,
    "hasMore" | "loadMore" | "loadingMore"
  >;
}) {
  const { t } = useTranslation("web");
  if (!paged.hasMore) return null;
  return (
    <div className="flex justify-center p-3">
      <Button size="sm" onClick={paged.loadMore} loading={paged.loadingMore}>
        {t("actions.loadMore")}
      </Button>
    </div>
  );
}
