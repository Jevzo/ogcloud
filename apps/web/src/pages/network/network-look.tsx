import type {
  NetworkSettingsFields,
  TextPreview,
  TextSpanView,
} from "@ogcloud/api-client";
import {
  type GameSpan,
  minecraftColours,
  PreviewFrame,
  Segmented,
  ServerListPreview,
  TablistPreview,
} from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import mascot from "../../assets/mascot-64.png";
import { type Client, ClientSwitch } from "../../client-switch.tsx";
import { useFormat } from "../../format.ts";
import { useTextPreview } from "../../texts.ts";

type State = "open" | "maintenance";

/** Breaks drawn spans into the lines their newlines start, as the server list shows a MOTD. */
function lines(spans: readonly TextSpanView[]): GameSpan[][] {
  const result: GameSpan[][] = [[]];
  for (const span of spans) {
    span.text.split("\n").forEach((part, index) => {
      if (index > 0) result.push([]);
      if (part !== "") result.at(-1)?.push({ ...span, text: part });
    });
  }
  return result;
}

const plain = (spans: readonly TextSpanView[]) =>
  spans.map((span) => span.text).join("");

const ENTRIES: readonly (readonly GameSpan[])[] = [
  [{ text: "mira", color: minecraftColours.gold }],
  [{ text: "kiwibear", color: minecraftColours.red }],
  [{ text: "Tobbe04", color: minecraftColours.gray }],
  [{ text: "redstoneRita", color: minecraftColours.gray }],
];

interface NetworkLookProps {
  settings: NetworkSettingsFields;
  online: number;
}

/** The network as players see it: its server list entry, open or in maintenance, and its tablist (D-0200). */
export function NetworkLook({ settings, online }: NetworkLookProps) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const [client, setClient] = useState<Client>("modern");
  const [state, setState] = useState<State>(
    settings.maintenance ? "maintenance" : "open",
  );
  const maintenance = state === "maintenance";
  const pick = (preview: { data?: TextPreview | undefined }) =>
    preview.data?.rendering?.[client] ?? [];
  const motd = pick(
    useTextPreview(
      maintenance ? "MAINTENANCE_MOTD" : "MOTD",
      maintenance ? settings.maintenanceMotd : settings.motd,
    ),
  );
  const version = pick(
    useTextPreview("MAINTENANCE_VERSION_NAME", settings.maintenanceVersionName),
  );
  const header = pick(useTextPreview("TABLIST_HEADER", settings.tablistHeader));
  const footer = pick(useTextPreview("TABLIST_FOOTER", settings.tablistFooter));

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2">
        <Segmented
          label={t("network.look.state")}
          options={[
            { value: "open", label: t("network.look.open") },
            { value: "maintenance", label: t("network.look.maintenance") },
          ]}
          value={state}
          onValueChange={setState}
        />
        <ClientSwitch value={client} onValueChange={setClient} />
      </div>
      <PreviewFrame label={t("network.look.serverList")}>
        <ServerListPreview
          icon={mascot}
          name={t("network.look.serverName")}
          motd={lines(motd)}
          players={
            maintenance
              ? plain(version)
              : `${format.number(online)}/${format.number(settings.maxPlayers)}`
          }
        />
      </PreviewFrame>
      {settings.tablistEnabled ? (
        <PreviewFrame label={t("permissions.look.tablist")}>
          <TablistPreview header={header} entries={ENTRIES} footer={footer} />
        </PreviewFrame>
      ) : null}
    </div>
  );
}
