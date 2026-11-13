import type { GroupOverview } from "@ogcloud/api-client";
import { Button, Card, Note } from "@ogcloud/ui";
import { TriangleAlert } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useCan } from "../../permissions.ts";

interface PausedCardProps {
  paused: readonly GroupOverview[];
  onResume: (groupId: string) => void;
}

/** Groups whose servers kept failing to start, each with the way to resume; absent while there are none. */
export function PausedCard({ paused, onResume }: PausedCardProps) {
  const { t } = useTranslation("web");
  const canResume = useCan("groups:write");
  if (paused.length === 0) return null;
  return (
    <Card title={t("overview.paused.title")}>
      <div className="flex flex-col gap-2">
        {paused.map((group) => (
          <Note
            key={group.id}
            tone="danger"
            icon={TriangleAlert}
            action={
              canResume ? (
                <Button size="sm" onClick={() => onResume(group.id)}>
                  {t("groups.actions.resume")}
                </Button>
              ) : undefined
            }
          >
            <div className="font-label">
              {t("overview.paused.group", { id: group.id })}
            </div>
            <div className="text-small text-text-2">
              {t("overview.paused.reason")}
            </div>
          </Note>
        ))}
      </div>
    </Card>
  );
}
