import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { renderCss, stylesheetUrl } from "./css.ts";
import { type ThemeColours, themeNames, themes } from "./tokens.ts";

const TEXT_MINIMUM = 4.5;
const CONTROL_MINIMUM = 3;
const TEXT_BACKGROUNDS = [
  "canvas",
  "sunken",
  "surface",
  "surface2",
  "surface3",
] as const;

// WCAG 2.2: relative luminance of an sRGB colour, and the contrast ratio of two.
function luminance(hex: string): number {
  const [r = 0, g = 0, b = 0] = [1, 3, 5].map((i) => {
    const channel = Number.parseInt(hex.slice(i, i + 2), 16) / 255;
    return channel <= 0.04045
      ? channel / 12.92
      : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return ((light ?? 0) + 0.05) / ((dark ?? 0) + 0.05);
}

function assertContrast(
  colours: ThemeColours,
  fg: keyof ThemeColours,
  bg: keyof ThemeColours,
  minimum: number,
) {
  const ratio = contrast(colours[fg], colours[bg]);
  assert.ok(
    ratio >= minimum,
    `${fg} on ${bg} is ${ratio.toFixed(2)}:1, below ${minimum}:1`,
  );
}

test("the committed stylesheet matches the tokens", () => {
  assert.equal(
    readFileSync(stylesheetUrl, "utf8"),
    renderCss(),
    "run `mise run gen:tokens` and commit the result",
  );
});

for (const name of themeNames) {
  const colours = themes[name].colours;

  test(`${name}: every text colour reaches 4.5:1 where it is used`, () => {
    for (const text of ["text", "text2", "text3"] as const) {
      for (const background of TEXT_BACKGROUNDS)
        assertContrast(colours, text, background, TEXT_MINIMUM);
    }
    assertContrast(colours, "accentText", "surface", TEXT_MINIMUM);
    assertContrast(colours, "accentText", "accentSoft", TEXT_MINIMUM);
    assertContrast(colours, "onAccent", "accent", TEXT_MINIMUM);
    assertContrast(colours, "onAccent", "dangerFill", TEXT_MINIMUM);
    assertContrast(colours, "successText", "successSoft", TEXT_MINIMUM);
    assertContrast(colours, "warningText", "warningSoft", TEXT_MINIMUM);
    assertContrast(colours, "dangerText", "dangerSoft", TEXT_MINIMUM);
    assertContrast(colours, "infoText", "infoSoft", TEXT_MINIMUM);
  });

  test(`${name}: control borders and chart series reach 3:1 on the surfaces`, () => {
    for (const background of ["surface", "surface2"] as const) {
      assertContrast(colours, "borderControl", background, CONTROL_MINIMUM);
    }
    for (const series of [
      "chart1",
      "chart2",
      "chart3",
      "chart4",
      "chart5",
    ] as const) {
      assertContrast(colours, series, "surface", CONTROL_MINIMUM);
    }
  });
}
