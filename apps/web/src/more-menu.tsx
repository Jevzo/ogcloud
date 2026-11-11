import { Button, Menu, type MenuGroup } from "@ogcloud/ui";
import { Ellipsis } from "lucide-react";
import { useTranslation } from "react-i18next";

/** A page's less frequent actions behind ⋯, first in its header row (D-0210); nothing when there are none. */
export function MoreMenu({ groups }: { groups: readonly MenuGroup[] }) {
  const { t } = useTranslation("web");
  if (groups.every((group) => Array.isArray(group) && group.length === 0))
    return null;
  return (
    <Menu
      groups={groups}
      trigger={
        <Button icon={Ellipsis} iconOnly>
          {t("actions.more")}
        </Button>
      }
    />
  );
}
