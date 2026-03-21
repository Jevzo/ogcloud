import type { CSSProperties } from "react";

export interface MinecraftRenderSegment {
    text: string;
    style: CSSProperties;
}

export const DEFAULT_MINECRAFT_TEXT_COLOR = "#e2e8f0";

const COLOR_CODES: Record<string, string> = {
    "0": "#000000",
    "1": "#0000aa",
    "2": "#00aa00",
    "3": "#00aaaa",
    "4": "#aa0000",
    "5": "#aa00aa",
    "6": "#ffaa00",
    "7": "#aaaaaa",
    "8": "#555555",
    "9": "#5555ff",
    a: "#55ff55",
    b: "#55ffff",
    c: "#ff5555",
    d: "#ff55ff",
    e: "#ffff55",
    f: "#ffffff",
};

const buildStyle = (
    color: string,
    bold: boolean,
    italic: boolean,
    underline: boolean,
    strikethrough: boolean,
): CSSProperties => {
    const decorations: string[] = [];

    if (underline) {
        decorations.push("underline");
    }
    if (strikethrough) {
        decorations.push("line-through");
    }

    return {
        color,
        fontWeight: bold ? 700 : 500,
        fontStyle: italic ? "italic" : "normal",
        textDecorationLine: decorations.length > 0 ? decorations.join(" ") : "none",
    };
};

export const parseMinecraftSegments = (rawValue: string): MinecraftRenderSegment[] => {
    const segments: MinecraftRenderSegment[] = [];
    let buffer = "";

    let color = DEFAULT_MINECRAFT_TEXT_COLOR;
    let bold = false;
    let italic = false;
    let underline = false;
    let strikethrough = false;
    let activeStyle = buildStyle(color, bold, italic, underline, strikethrough);

    const flushBuffer = () => {
        if (!buffer) {
            return;
        }

        segments.push({
            text: buffer,
            style: activeStyle,
        });
        buffer = "";
    };

    for (let index = 0; index < rawValue.length; index += 1) {
        const currentChar = rawValue[index];
        const nextChar = rawValue[index + 1]?.toLowerCase();
        const isFormattingPrefix = currentChar === "&" || currentChar === "\u00a7";

        if (!isFormattingPrefix || !nextChar) {
            buffer += currentChar;
            continue;
        }

        if (!(nextChar in COLOR_CODES) && !"lmnor".includes(nextChar)) {
            buffer += currentChar;
            continue;
        }

        flushBuffer();

        if (nextChar in COLOR_CODES) {
            color = COLOR_CODES[nextChar];
            bold = false;
            italic = false;
            underline = false;
            strikethrough = false;
        } else {
            switch (nextChar) {
                case "l":
                    bold = true;
                    break;
                case "m":
                    strikethrough = true;
                    break;
                case "n":
                    underline = true;
                    break;
                case "o":
                    italic = true;
                    break;
                case "r":
                    color = DEFAULT_MINECRAFT_TEXT_COLOR;
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    break;
                default:
                    break;
            }
        }

        activeStyle = buildStyle(color, bold, italic, underline, strikethrough);
        index += 1;
    }

    flushBuffer();
    return segments;
};

export const stripMinecraftFormattingCodes = (rawValue: string) =>
    rawValue.replace(/(?:&|\u00a7)[0-9a-fk-or]/gi, "");

export const hasMeaningfulVisibleContent = (rawValue: string) => {
    const stripped = stripMinecraftFormattingCodes(rawValue);
    return /[a-z0-9]/i.test(stripped);
};

export const resolveMinecraftTextColor = (
    rawValue: string | null | undefined,
    fallback = DEFAULT_MINECRAFT_TEXT_COLOR,
) => {
    const normalizedValue = rawValue?.trim();

    if (!normalizedValue) {
        return fallback;
    }

    let color = fallback;

    for (let index = 0; index < normalizedValue.length; index += 1) {
        const currentChar = normalizedValue[index];
        const nextChar = normalizedValue[index + 1]?.toLowerCase();
        const isFormattingPrefix = currentChar === "&" || currentChar === "\u00a7";

        if (!isFormattingPrefix || !nextChar) {
            continue;
        }

        if (nextChar in COLOR_CODES) {
            color = COLOR_CODES[nextChar];
            index += 1;
            continue;
        }

        if (nextChar === "r") {
            color = fallback;
            index += 1;
        }
    }

    return color;
};
