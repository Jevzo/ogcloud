import assert from "node:assert/strict";
import { test } from "node:test";
import { everyStep } from "../steps.ts";

test("every step runs, also after one fails, and the failures name their steps", async () => {
  const ran: string[] = [];
  const failures = await everyStep([
    ["first", async () => ran.push("first")],
    [
      "second",
      async () => {
        throw new Error("broken");
      },
    ],
    ["third", async () => ran.push("third")],
  ]);

  assert.deepEqual(ran, ["first", "third"]);
  assert.deepEqual(
    failures.map((failure) => failure.message),
    ["second: broken"],
  );
});
