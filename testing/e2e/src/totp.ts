import { createHmac } from "node:crypto";

// RFC 6238 as the API computes it (D-0084): HMAC-SHA1, 30-second steps, six digits.
const STEP_SECONDS = 30;
const DIGITS = 6;
const BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

export function step(at: Date): number {
  return Math.floor(at.getTime() / 1000 / STEP_SECONDS);
}

/** When the step after [at] begins. */
export function nextStepAt(at: Date): Date {
  return new Date((step(at) + 1) * STEP_SECONDS * 1000);
}

export function code(secret: Uint8Array, counter: number): string {
  const message = Buffer.alloc(8);
  message.writeBigUInt64BE(BigInt(counter));
  const mac = createHmac("sha1", secret).update(message).digest();
  const offset = (mac.at(-1) ?? 0) & 0x0f;
  const truncated = mac.readUInt32BE(offset) & 0x7fffffff;
  return String(truncated % 10 ** DIGITS).padStart(DIGITS, "0");
}

/** RFC 4648 base32, as the setup page shows the secret. */
export function base32Decode(text: string): Uint8Array {
  const bytes: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const char of text.replace(/=+$/, "").toUpperCase()) {
    const value = BASE32.indexOf(char);
    if (value < 0) {
      throw new Error(`Not base32: ${char}`);
    }
    buffer = (buffer << 5) | value;
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((buffer >> bits) & 0xff);
    }
  }
  return Uint8Array.from(bytes);
}
