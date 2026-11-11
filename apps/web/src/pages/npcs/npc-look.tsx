import type { TextPreview } from "@ogcloud/api-client";
import { NametagPreview, PreviewFrame } from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { type Client, ClientSwitch } from "../../client-switch.tsx";
import { GameChip } from "../../game-chip.tsx";
import { useTextPreview } from "../../texts.ts";

const pick = (preview: { data?: TextPreview | undefined }, client: Client) =>
  preview.data?.rendering?.[client] ?? [];

/** An NPC's title and subtitle as they float over it, with the samples the API fills in (D-0200). */
export function NpcLook({
  title,
  subtitle,
}: {
  title: string;
  subtitle: string;
}) {
  const { t } = useTranslation("web");
  const [client, setClient] = useState<Client>("modern");
  const lines = [
    pick(useTextPreview("NPC_TITLE", title), client),
    pick(useTextPreview("NPC_SUBTITLE", subtitle), client),
  ].filter((spans) => spans.length > 0);
  return (
    <div className="flex flex-col gap-3">
      <ClientSwitch value={client} onValueChange={setClient} />
      <PreviewFrame label={t("npcs.look.over")}>
        <NametagPreview lines={lines} />
      </PreviewFrame>
    </div>
  );
}

/** An NPC's title drawn small, for tables. */
export function NpcTitleChip({ title }: { title: string }) {
  return (
    <GameChip spans={pick(useTextPreview("NPC_TITLE", title), "modern")} />
  );
}
