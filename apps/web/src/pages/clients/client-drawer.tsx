import type { OAuthClientView } from "@ogcloud/api-client";
import { useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { DetailRow } from "../../details.tsx";
import { useFormat } from "../../format.ts";
import { useHeld } from "../../held.ts";
import { RecordDrawer } from "../../record-drawer.tsx";

/** A list row's client beside the list, with the way to its page (D-0231). */
export function ClientDrawer({
  client: current,
  onClose,
}: {
  client: OAuthClientView | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const format = useFormat();
  // The drawer slides out still showing what it showed.
  const client = useHeld(current);
  if (client === null) return null;
  return (
    <RecordDrawer
      open={current !== null}
      title={client.name}
      onClose={onClose}
      onOpenPage={() =>
        void navigate({
          to: "/clients/$clientId",
          params: { clientId: client.clientId },
        })
      }
    >
      <DetailRow label={t("clients.columns.kind")}>
        {client.firstParty ? t("clients.firstParty") : t("clients.machine")}
      </DetailRow>
      <DetailRow label={t("clients.columns.clientId")}>
        <span className="font-mono text-mono">{client.clientId}</span>
      </DetailRow>
      <DetailRow label={t("clients.columns.permissions")}>
        {client.firstParty
          ? t("clients.fromUser")
          : format.number(client.scopes.length)}
      </DetailRow>
      <DetailRow label={t("clients.columns.created")}>
        <Ago at={client.createdAt} className="" />
      </DetailRow>
    </RecordDrawer>
  );
}
