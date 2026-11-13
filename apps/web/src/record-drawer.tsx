import { Button, Drawer } from "@ogcloud/ui";
import { ArrowUpRight } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Details } from "./details.tsx";

interface RecordDrawerProps {
  title: ReactNode;
  badges?: ReactNode;
  onClose: () => void;
  /** Goes to the record's own page, which stays its editor (D-0210). */
  onOpenPage: () => void;
  /** Everyday actions before "Open page", which comes last as the primary one. */
  actions?: ReactNode;
  /** Detail rows: the record's key facts. */
  children: ReactNode;
}

/** A list row's record beside the list: its key facts, its everyday actions and the way to its page (D-0231). */
export function RecordDrawer({
  title,
  badges,
  onClose,
  onOpenPage,
  actions,
  children,
}: RecordDrawerProps) {
  const { t } = useTranslation("web");
  return (
    <Drawer
      open
      onOpenChange={(open) => (open ? undefined : onClose())}
      title={title}
      badges={badges}
      footer={
        <>
          {actions}
          <Button kind="primary" icon={ArrowUpRight} onClick={onOpenPage}>
            {t("actions.openPage")}
          </Button>
        </>
      }
    >
      <Details>{children}</Details>
    </Drawer>
  );
}
