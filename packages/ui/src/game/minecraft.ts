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
