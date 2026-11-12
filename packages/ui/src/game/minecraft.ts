/** Minecraft's sixteen named colours, by their tag names (D-0200). */
export const minecraftColours = {
  black: "#000000",
  dark_blue: "#0000AA",
  dark_green: "#00AA00",
  dark_aqua: "#00AAAA",
  dark_red: "#AA0000",
  dark_purple: "#AA00AA",
  gold: "#FFAA00",
  gray: "#AAAAAA",
  dark_gray: "#555555",
  blue: "#5555FF",
  green: "#55FF55",
  aqua: "#55FFFF",
  red: "#FF5555",
  light_purple: "#FF55FF",
  yellow: "#FFFF55",
  white: "#FFFFFF",
} as const;

export type MinecraftColour = keyof typeof minecraftColours;

/** The game's own surfaces and default text colours, which previews show in both themes (D-0209). */
export const gameLook = {
  panel: "#141414",
  edge: "#000000",
  chat: "rgba(0, 0, 0, 0.5)",
  tablist: "rgba(0, 0, 0, 0.6)",
  tablistEntry: "rgba(255, 255, 255, 0.12)",
  nametag: "rgba(0, 0, 0, 0.25)",
  sky: "#7EA4DB",
  /** Server-list descriptions and counts are drawn in this grey when no colour is set. */
  serverListText: "#808080",
  text: "#FFFFFF",
} as const;

/** The game draws each glyph's shadow in the same colour at a quarter of its brightness. */
export function shadowOf(hex: string): string {
  const channels = [1, 3, 5].map((i) =>
    Math.floor(Number.parseInt(hex.slice(i, i + 2), 16) / 4),
  );
  return `#${channels.map((c) => c.toString(16).padStart(2, "0")).join("")}`;
}

// The default font's glyph widths in game pixels, as its bitmap draws them; every other glyph is 5 wide.
const NARROW_GLYPHS: Readonly<Record<string, number>> = {
  " ": 3,
  "!": 1,
  '"': 3,
  "'": 1,
  "(": 4,
  ")": 4,
  ",": 1,
  ".": 1,
  ":": 1,
  ";": 1,
  "<": 4,
  ">": 4,
  "@": 6,
  I: 3,
  "[": 3,
  "]": 3,
  "`": 2,
  f: 4,
  i: 1,
  k: 4,
  l: 2,
  t: 3,
  "{": 4,
  "|": 1,
  "}": 4,
  "~": 6,
};
const GLYPH_WIDTH = 5;
// One pixel of spacing follows every glyph; bold draws each glyph twice, a pixel apart.
const GLYPH_SPACING = 1;
const BOLD_EXTRA = 1;

/** How far the game moves on after drawing [char], in game pixels. */
export function advanceOf(char: string, bold: boolean): number {
  return (
    (NARROW_GLYPHS[char] ?? GLYPH_WIDTH) +
    GLYPH_SPACING +
    (bold ? BOLD_EXTRA : 0)
  );
}

interface Glyph {
  char: string;
  span: number;
}

/**
 * One line of spans broken into lines no wider than [width] game pixels, as the game's line splitter does: at the
 * last space that fits, which it drops, or mid-word when a word alone is too wide.
 */
export function splitToWidth<S extends { text: string; bold?: boolean }>(
  spans: readonly S[],
  width: number,
): S[][] {
  const glyphs: Glyph[] = spans.flatMap((span, index) =>
    Array.from(span.text, (char) => ({ char, span: index })),
  );
  const advance = (glyph: Glyph) =>
    advanceOf(glyph.char, spans[glyph.span]?.bold === true);
  const lines: Glyph[][] = [];
  let line: Glyph[] = [];
  let used = 0;
  for (const glyph of glyphs) {
    if (used + advance(glyph) > width && line.length > 0) {
      if (glyph.char === " ") {
        lines.push(line);
        line = [];
        used = 0;
        continue;
      }
      const space = line.findLastIndex((candidate) => candidate.char === " ");
      const rest = space < 0 ? [] : line.slice(space + 1);
      lines.push(space < 0 ? line : line.slice(0, space));
      line = rest;
      used = rest.reduce((sum, kept) => sum + advance(kept), 0);
    }
    line.push(glyph);
    used += advance(glyph);
  }
  lines.push(line);
  return lines.map((glyphLine) => spansOf(glyphLine, spans));
}

/** Regroups a line's glyphs into the spans they came from, so each keeps its colour and decorations. */
function spansOf<S extends { text: string }>(
  glyphs: readonly Glyph[],
  spans: readonly S[],
): S[] {
  const runs: S[] = [];
  let current: { span: number; text: string } | undefined;
  for (const glyph of glyphs) {
    if (current?.span === glyph.span) {
      current.text += glyph.char;
    } else {
      if (current !== undefined) runs.push(runOf(current, spans));
      current = { span: glyph.span, text: glyph.char };
    }
  }
  if (current !== undefined) runs.push(runOf(current, spans));
  return runs;
}

function runOf<S extends { text: string }>(
  run: { span: number; text: string },
  spans: readonly S[],
): S {
  const source = spans[run.span];
  if (source === undefined) throw new Error(`No span ${run.span} to split`);
  return { ...source, text: run.text };
}
