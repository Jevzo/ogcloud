import type { TextSpanView } from "@ogcloud/api-client";
import { GameText, gameLook } from "@ogcloud/ui";

/** In-game text drawn small on the game's dark backdrop, for tables. */
export function GameChip({ spans }: { spans: readonly TextSpanView[] }) {
  return (
    <span
      className="inline-block rounded-small px-1.5 py-0.5 font-game text-small whitespace-pre"
      style={{ backgroundColor: gameLook.panel }}
    >
      <GameText spans={spans} defaultColour={gameLook.text} />
    </span>
  );
}
