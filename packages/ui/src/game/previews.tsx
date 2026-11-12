import type { ReactNode } from "react";
import { cn } from "../lib/cn.ts";
import { type GameSpan, GameText } from "./game-text.tsx";
import { gameLook, splitToWidth } from "./minecraft.ts";

const gameFont = "font-game text-game game-selection";

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
  /** The MOTD's lines; the game wraps them to its width and shows two. */
  motd: readonly (readonly GameSpan[])[];
  players: string;
}

// The server list's row in game pixels: a 32 pixel icon, text from 35, the MOTD split at 271 pixels.
const SERVER_ROW = {
  width: 305,
  icon: 32,
  textGap: 3,
  nameTop: 1,
  motdGap: 2,
  motdWidth: 271,
  motdLines: 2,
} as const;
// Monocraft draws on nine units an em, caps seven high, and every glyph moves on six: one unit is a game pixel.
const UNITS_PER_EM = 9;
const MONOCRAFT_ADVANCE = 6;
const ROW_PADDING = 2;

const gamePixels = (pixels: number) => `${pixels / UNITS_PER_EM}em`;

/**
 * A server-list row drawn to the game's measure, scaled to the frame's width and no larger than other previews:
 * the MOTD breaks where the game breaks it, and a line Monocraft draws wider than the game is narrowed to fit.
 */
export function ServerListPreview({
  icon,
  name,
  motd,
  players,
}: ServerListPreviewProps) {
  const lines = motd
    .flatMap((line) => splitToWidth(line, SERVER_ROW.motdWidth))
    .slice(0, SERVER_ROW.motdLines);
  const rowWidth = SERVER_ROW.width + 2 * ROW_PADDING;
  return (
    <div className="@container">
      <div
        data-skeleton=""
        className={cn("flex rounded-game border-2", gameFont)}
        style={{
          fontSize: `min(var(--text-game), calc(100cqw * ${UNITS_PER_EM} / ${rowWidth}))`,
          lineHeight: 1,
          padding: gamePixels(ROW_PADDING),
          gap: gamePixels(SERVER_ROW.textGap),
          backgroundColor: gameLook.panel,
          borderColor: gameLook.edge,
        }}
      >
        <img
          src={icon}
          alt=""
          className="shrink-0 [image-rendering:pixelated]"
          style={{
            width: gamePixels(SERVER_ROW.icon),
            height: gamePixels(SERVER_ROW.icon),
          }}
        />
        <div
          className="min-w-0 flex-1"
          style={{ paddingTop: gamePixels(SERVER_ROW.nameTop) }}
        >
          <div className="flex">
            <span className="flex-1 truncate">
              <GameText
                spans={[{ text: name }]}
                defaultColour={gameLook.text}
              />
            </span>
            <GameText
              spans={[{ text: players }]}
              defaultColour={gameLook.serverListText}
            />
          </div>
          <div style={{ marginTop: gamePixels(SERVER_ROW.motdGap) }}>
            {lines.map((spans, index) => (
              <div key={index} className="overflow-hidden whitespace-pre">
                <span
                  className="inline-block origin-left"
                  style={{ transform: `scaleX(${narrowing(spans)})` }}
                >
                  <GameText
                    spans={spans}
                    defaultColour={gameLook.serverListText}
                  />
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/** How much a line must narrow so Monocraft, whose glyphs are all six wide, fits it where the game does. */
function narrowing(spans: readonly GameSpan[]): number {
  const glyphs = spans.reduce(
    (count, span) => count + Array.from(span.text).length,
    0,
  );
  return Math.min(1, SERVER_ROW.motdWidth / (glyphs * MONOCRAFT_ADVANCE));
}

export function ChatPreview({
  lines,
}: {
  lines: readonly (readonly GameSpan[])[];
}) {
  return (
    <div
      data-skeleton=""
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
      data-skeleton=""
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
      data-skeleton=""
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
