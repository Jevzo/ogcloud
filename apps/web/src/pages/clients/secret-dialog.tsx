import type { OAuthClientCredentials } from "@ogcloud/api-client";
import { Button, CopyField, Dialog, Note } from "@ogcloud/ui";
import { TriangleAlert } from "lucide-react";
import { useTranslation } from "react-i18next";

/** A machine client's id and secret, shown once: OgCloud keeps only the secret's hash (D-0040). */
export function SecretDialog({
  credentials,
  onClose,
}: {
  credentials: OAuthClientCredentials | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("web");
  return (
    <Dialog
      open={credentials !== null}
      onOpenChange={(open) => (open ? undefined : onClose())}
      title={t("clients.secret.title", { name: credentials?.name ?? "" })}
      description={t("clients.secret.description")}
      footer={
        <Button kind="primary" onClick={onClose}>
          {t("clients.secret.done")}
        </Button>
      }
    >
      <Note tone="warning" icon={TriangleAlert}>
        {t("clients.secret.once")}
      </Note>
      <div className="flex flex-col gap-1.5">
        <span className="font-label">{t("clients.columns.clientId")}</span>
        <CopyField
          value={credentials?.clientId ?? ""}
          label={t("clients.columns.clientId")}
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="font-label">{t("clients.secret.secret")}</span>
        <CopyField
          value={credentials?.clientSecret ?? ""}
          label={t("clients.secret.secret")}
        />
      </div>
    </Dialog>
  );
}
