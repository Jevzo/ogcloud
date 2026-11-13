import { usePermissionGroupList } from "@ogcloud/api-client/react";
import { Trans, useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { useCan } from "../../permissions.ts";
import { RankPrefixChip } from "../permissions/rank-look.tsx";

// Ranks change rarely; one list serves every row.
const GROUPS_STALE_MS = 60_000;

/** A player's rank as its chat prefix shows in game, with when it ends, muted (D-0231). */
export function RankCell({
  groupId,
  expiresAt,
}: {
  groupId: string;
  expiresAt?: string | null | undefined;
}) {
  const { t } = useTranslation("web");
  const canRead = useCan("permissions:read");
  const groups = usePermissionGroupList({
    query: { enabled: canRead, staleTime: GROUPS_STALE_MS },
  });
  const display = groups.data?.data.find((group) => group.id === groupId)
    ?.settings.display;
  return (
    <span className="inline-flex items-center gap-2">
      {display === undefined ? (
        <span className="text-text-2">{groupId}</span>
      ) : (
        <RankPrefixChip display={display} id={groupId} />
      )}
      {expiresAt == null ? null : (
        <span className="text-meta text-text-3">
          <Trans
            t={t}
            i18nKey="players.ends"
            components={{ ago: <Ago at={expiresAt} className="" /> }}
          />
        </span>
      )}
    </span>
  );
}
