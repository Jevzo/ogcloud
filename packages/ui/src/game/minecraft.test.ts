import assert from "node:assert/strict";
import { test } from "node:test";
import { advanceOf, splitToWidth } from "./minecraft.ts";

const texts = (lines: { text: string }[][]) =>
  lines.map((line) => line.map((span) => span.text).join(""));

test("glyphs advance by their width plus a pixel, and one more when bold", () => {
  assert.equal(advanceOf("a", false), 6);
  assert.equal(advanceOf("i", false), 2);
  assert.equal(advanceOf(" ", false), 4);
  assert.equal(advanceOf("a", true), 7);
});

test("a line that fits stays whole", () => {
  assert.deepEqual(texts(splitToWidth([{ text: "hello" }], 30)), ["hello"]);
});

test("a line breaks at the last space that fits, which it drops", () => {
  // "aaa bbb" is 6*3 + 4 + 6*3 = 40 pixels.
  assert.deepEqual(texts(splitToWidth([{ text: "aaa bbb" }], 30)), [
    "aaa",
    "bbb",
  ]);
});

test("a space that overflows ends the line and is dropped", () => {
  assert.deepEqual(texts(splitToWidth([{ text: "aaaaa b" }], 30)), [
    "aaaaa",
    "b",
  ]);
});

test("a word wider than the line breaks mid-word", () => {
  assert.deepEqual(texts(splitToWidth([{ text: "aaaaaaa" }], 30)), [
    "aaaaa",
    "aa",
  ]);
});

test("narrow glyphs let more of a line fit than the common width would", () => {
  // Five pixels each with spacing would be 6 * 10 = 60; "illillilli" is 2 and 3 pixels a glyph.
  assert.deepEqual(texts(splitToWidth([{ text: "illillilli" }], 30)), [
    "illillilli",
  ]);
});

test("spans keep their styles on both sides of a break", () => {
  const lines = splitToWidth(
    [
      { text: "aaa ", color: "#FF5555" },
      { text: "bbb", color: "#55FF55", bold: true },
    ],
    30,
  );
  assert.deepEqual(lines, [
    [{ text: "aaa", color: "#FF5555" }],
    [{ text: "bbb", color: "#55FF55", bold: true }],
  ]);
});
