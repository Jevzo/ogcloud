import type { ReactNode } from "react";
import { cn } from "../lib/cn.ts";
import { type GameSpan, GameText } from "./game-text.tsx";
import { gameLook } from "./minecraft.ts";

const gameFont = "font-game text-game";

/** A labelled preview of how one client version shows a text. */
export function PreviewFrame({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <figure className="m-0 flex flex-col gap-1">
      <figcaption className="text-meta text-text-3">{label}</figcaption>
      {children}
    </figure>
  );
}

interface ServerListPreviewProps {
  icon: string;
  name: string;
  /** The MOTD's lines; the game shows two. */
  motd: readonly (readonly GameSpan[])[];
  players: string;
}

export function ServerListPreview({
  icon,
  name,
  motd,
  players,
}: ServerListPreviewProps) {
  return (
    <div
      className={cn("flex gap-2.5 rounded-game border-2 p-2", gameFont)}
      style={{ backgroundColor: gameLook.panel, borderColor: gameLook.edge }}
    >
      <img
        src={icon}
        alt=""
        width={60}
        height={60}
        className="size-15 shrink-0 [image-rendering:pixelated]"
      />
      <div className="min-w-0 flex-1">
        <div className="flex">
          <span className="flex-1 truncate">
            <GameText spans={[{ text: name }]} defaultColour={gameLook.text} />
          </span>
          <GameText
            spans={[{ text: players }]}
            defaultColour={gameLook.serverListText}
          />
        </div>
        {motd.map((spans, index) => (
          <div key={index} className="truncate whitespace-pre">
            <GameText spans={spans} defaultColour={gameLook.serverListText} />
          </div>
        ))}
      </div>
    </div>
  );
}

export function ChatPreview({
  lines,
}: {
  lines: readonly (readonly GameSpan[])[];
}) {
  return (
    <div
      className={cn("rounded-game px-2.5 py-2", gameFont)}
      style={{ backgroundColor: gameLook.chat }}
    >
      {lines.map((spans, index) => (
        <div key={index} className="whitespace-pre-wrap">
          <GameText spans={spans} defaultColour={gameLook.text} />
        </div>
      ))}
    </div>
  );
}

interface TablistPreviewProps {
  header: readonly GameSpan[];
  entries: readonly (readonly GameSpan[])[];
  footer: readonly GameSpan[];
}

export function TablistPreview({
  header,
  entries,
  footer,
}: TablistPreviewProps) {
  return (
    <div
      className={cn("border-2 px-2.5 py-2 text-center", gameFont)}
      style={{ backgroundColor: gameLook.tablist, borderColor: gameLook.edge }}
    >
      <div className="whitespace-pre-wrap">
        <GameText spans={header} defaultColour={gameLook.text} />
      </div>
      <div className="my-1.5 grid grid-cols-2 gap-0.5 text-left">
        {entries.map((spans, index) => (
          <div
            key={index}
            className="truncate px-1.5"
            style={{ backgroundColor: gameLook.tablistEntry }}
          >
            <GameText spans={spans} defaultColour={gameLook.text} />
          </div>
        ))}
      </div>
      <div className="whitespace-pre-wrap">
        <GameText spans={footer} defaultColour={gameLook.text} />
      </div>
    </div>
  );
}

/** Lines floating over an NPC or a player, each on the game's translucent plate. */
export function NametagPreview({
  lines,
}: {
  lines: readonly (readonly GameSpan[])[];
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-1 rounded-game px-2 py-4",
        gameFont,
      )}
      style={{ backgroundColor: gameLook.sky }}
    >
      {lines.map((spans, index) => (
        <span
          key={index}
          className="px-1"
          style={{ backgroundColor: gameLook.nametag }}
        >
          <GameText spans={spans} defaultColour={gameLook.text} />
        </span>
      ))}
    </div>
  );
}
