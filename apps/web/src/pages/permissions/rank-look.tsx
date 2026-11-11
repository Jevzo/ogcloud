import type { RankDisplayView, TextPreview } from "@ogcloud/api-client";
import {
  ChatPreview,
  type GameSpan,
  minecraftColours,
  NametagPreview,
  PreviewFrame,
  TablistPreview,
} from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { type Client, ClientSwitch } from "../../client-switch.tsx";
import { GameChip } from "../../game-chip.tsx";
import { useTextPreview } from "../../texts.ts";

/** A rank's texts as the API draws them for one kind of client (D-0202); invalid texts draw as nothing. */
function useRankSpans(display: RankDisplayView, client: Client) {
  const pick = (preview: { data?: TextPreview | undefined }) =>
    preview.data?.rendering?.[client] ?? [];
  return {
    prefix: pick(useTextPreview("CHAT_PREFIX", display.chatPrefix)),
    name: pick(useTextPreview("NAME_FORMAT", display.nameFormat)),
    suffix: pick(useTextPreview("CHAT_SUFFIX", display.chatSuffix)),
    tab: pick(useTextPreview("TAB_PREFIX", display.tabPrefix)),
  };
}

const OTHERS: readonly (readonly GameSpan[])[] = [
  [{ text: "Tobbe04", color: minecraftColours.gray }],
  [{ text: "redstoneRita", color: minecraftColours.gray }],
  [{ text: "kiwibear", color: minecraftColours.gray }],
];

/** How a rank looks in chat, in the tablist and over a player's head, on 1.16+ and on older clients (D-0200). */
export function RankLook({ display }: { display: RankDisplayView }) {
  const { t } = useTranslation("web");
  const [client, setClient] = useState<Client>("modern");
  const spans = useRankSpans(display, client);
  const message: GameSpan[] = [
    { text: ": ", color: minecraftColours.gray },
    { text: t("permissions.look.message") },
  ];
  return (
    <div className="flex flex-col gap-3">
      <ClientSwitch value={client} onValueChange={setClient} />
      <PreviewFrame label={t("permissions.look.chat")}>
        <ChatPreview
          lines={[
            [...spans.prefix, ...spans.name, ...spans.suffix, ...message],
          ]}
        />
      </PreviewFrame>
      <PreviewFrame label={t("permissions.look.tablist")}>
        <TablistPreview
          header={[{ text: "OgWars Network", color: minecraftColours.gold }]}
          entries={[[...spans.tab, ...spans.name], ...OTHERS]}
          footer={[{ text: "1,284 online", color: minecraftColours.gray }]}
        />
      </PreviewFrame>
      <PreviewFrame label={t("permissions.look.nametag")}>
        <NametagPreview lines={[[...spans.tab, ...spans.name]]} />
      </PreviewFrame>
    </div>
  );
}

/** A rank's chat prefix and name in one line, small, for tables. */
export function RankChip({ display }: { display: RankDisplayView }) {
  const spans = useRankSpans(display, "modern");
  return <GameChip spans={[...spans.prefix, ...spans.name]} />;
}
