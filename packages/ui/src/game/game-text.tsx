import { useReducedMotion } from "motion/react";
import { useEffect, useState } from "react";
import { shadowOf } from "./minecraft.ts";

/** One run of styled text, as the API's text preview returns it (`TextSpanView`). */
export interface GameSpan {
  text: string;
  color?: string | null;
  bold?: boolean;
  italic?: boolean;
  underlined?: boolean;
  strikethrough?: boolean;
  obfuscated?: boolean;
}

const OBFUSCATED_GLYPHS =
  "ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789#$%&?";
const OBFUSCATED_TICK_MS = 80;

/** Spans the way the game draws them: Monocraft, a shadow at a quarter of each colour's brightness. */
export function GameText({
  spans,
  defaultColour,
}: {
  spans: readonly GameSpan[];
  defaultColour: string;
}) {
  return (
    <>
      {spans.map((span, index) => {
        const colour = span.color ?? defaultColour;
        const decorations = [
          span.underlined ? "underline" : "",
          span.strikethrough ? "line-through" : "",
        ]
          .join(" ")
          .trim();
        return (
          <span
            key={index}
            style={{
              color: colour,
              textShadow: `0.125em 0.125em 0 ${shadowOf(colour)}`,
              fontWeight: span.bold ? 700 : 400,
              fontStyle: span.italic ? "italic" : "normal",
              textDecorationLine: decorations === "" ? "none" : decorations,
            }}
          >
            {span.obfuscated ? <Obfuscated text={span.text} /> : span.text}
          </span>
        );
      })}
    </>
  );
}

/** Obfuscated text keeps changing in game; under reduced motion it stays still. */
function Obfuscated({ text }: { text: string }) {
  const still = useReducedMotion() ?? false;
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (still) return;
    const timer = setInterval(
      () => setTick((value) => value + 1),
      OBFUSCATED_TICK_MS,
    );
    return () => clearInterval(timer);
  }, [still]);
  return (
    <span aria-hidden="true">
      {Array.from(text, (character, index) =>
        character === " "
          ? " "
          : OBFUSCATED_GLYPHS[
              (index * 31 + tick * 17) % OBFUSCATED_GLYPHS.length
            ],
      ).join("")}
    </span>
  );
}
