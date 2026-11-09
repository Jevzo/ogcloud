import assert from "node:assert/strict";
import { test } from "node:test";
import { base32Decode, code, nextStepAt, step } from "../totp.ts";

// RFC 6238, appendix B: the SHA-1 secret is "12345678901234567890", and the vectors' last six digits are ours.
const RFC_SECRET = new TextEncoder().encode("12345678901234567890");

test("codes match RFC 6238's SHA-1 vectors", () => {
  const vectors: [number, string][] = [
    [59, "287082"],
    [1111111109, "081804"],
    [1111111111, "050471"],
    [1234567890, "005924"],
    [2000000000, "279037"],
  ];
  for (const [seconds, expected] of vectors) {
    assert.equal(code(RFC_SECRET, step(new Date(seconds * 1000))), expected);
  }
});

test("base32 decodes the way the setup page encodes", () => {
  assert.deepEqual(
    base32Decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"),
    RFC_SECRET,
  );
  assert.throws(() => base32Decode("GEZ1"), /Not base32/);
});

test("the next step starts on the next 30-second boundary", () => {
  assert.equal(nextStepAt(new Date(59_000)).getTime(), 60_000);
  assert.equal(nextStepAt(new Date(60_000)).getTime(), 90_000);
});
