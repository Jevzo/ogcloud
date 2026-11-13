import { Card, CopyField } from "@ogcloud/ui";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Ago } from "./ago.tsx";
import { DetailRow, Details } from "./details.tsx";

interface IdentityCardProps {
  /** What the id is called where it is used: "Server id", "UUID", "Client id". */
  label: string;
  id: string;
  createdAt?: string;
  /** One line on what the id is for, only where it has a use beyond this page. */
  hint?: string;
  /** Further facts about who or what this is, as detail rows. */
  children?: ReactNode;
}

/** The one way a detail page shows what its subject is: a labelled id to copy, then when it was made (D-0231). */
export function IdentityCard({
  label,
  id,
  createdAt,
  hint,
  children,
}: IdentityCardProps) {
  const { t } = useTranslation("web");
  return (
    <Card title={t("identity.title")}>
      <div className="flex flex-col gap-3">
        <div className="flex flex-col gap-1.5">
          <span className="font-label">{label}</span>
          <CopyField value={id} label={label} />
          {hint === undefined ? null : (
            <span className="text-small text-text-3">{hint}</span>
          )}
        </div>
        {createdAt === undefined && children === undefined ? null : (
          <Details>
            {children}
            {createdAt === undefined ? null : (
              <DetailRow label={t("identity.created")}>
                <Ago at={createdAt} className="" />
              </DetailRow>
            )}
          </Details>
        )}
      </div>
    </Card>
  );
}
