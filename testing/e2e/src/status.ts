import { connect } from "node:net";
import type { Target } from "./target.ts";

/** Client protocol numbers the server list scenario pings with: 1.20.4, which shows hex colours, and 1.8. */
export const HEX_PROTOCOL = 765;
export const LEGACY_PROTOCOL = 47;

const STATUS_STATE = 1;
const VARINT_BITS = 7;
const VARINT_MORE = 0x80;
const VARINT_MASK = 0x7f;
const TIMEOUT_MS = 10_000;

/** The load balancer's answer to a status ping from a client of [protocol]: what the server list shows. */
export function serverList(
  target: Target,
  protocol: number,
): Promise<{ description: unknown }> {
  return new Promise((resolve, reject) => {
    const socket = connect(target.minecraftPort, target.minecraftHost);
    let received = Buffer.alloc(0);
    let settled = false;
    const settle = (finish: () => void) => {
      if (!settled) {
        settled = true;
        socket.destroy();
        finish();
      }
    };
    const fail = (why: string) =>
      settle(() =>
        reject(new Error(`${why}; received ${received.toString("hex")}`)),
      );
    socket.setTimeout(TIMEOUT_MS, () =>
      fail(`No status answer within ${TIMEOUT_MS} ms`),
    );
    socket.on("error", (error) => settle(() => reject(error)));
    socket.on("close", () =>
      fail("The load balancer closed before a whole status answer"),
    );
    socket.on("data", (chunk) => {
      received = Buffer.concat([received, Buffer.from(chunk)]);
      const length = readVarInt(received, 0);
      if (
        length === undefined ||
        received.length < length.next + length.value
      ) {
        return;
      }
      const id = readVarInt(received, length.next);
      const size = id && readVarInt(received, id.next);
      if (id?.value !== 0 || size === undefined) {
        fail("Not a status response");
        return;
      }
      const json = received.toString("utf8", size.next, size.next + size.value);
      try {
        const answer = JSON.parse(json) as { description: unknown };
        settle(() => resolve(answer));
      } catch (error) {
        settle(() =>
          reject(
            new Error(`The status answer is no JSON: ${json}`, {
              cause: error,
            }),
          ),
        );
      }
    });
    const port = Buffer.alloc(2);
    port.writeUInt16BE(target.minecraftPort);
    const handshake = Buffer.concat([
      varInt(0),
      varInt(protocol),
      string(target.minecraftHost),
      port,
      varInt(STATUS_STATE),
    ]);
    socket.write(Buffer.concat([packet(handshake), packet(varInt(0))]));
  });
}

function packet(body: Buffer): Buffer {
  return Buffer.concat([varInt(body.length), body]);
}

function string(text: string): Buffer {
  const bytes = Buffer.from(text, "utf8");
  return Buffer.concat([varInt(bytes.length), bytes]);
}

function varInt(value: number): Buffer {
  const bytes: number[] = [];
  let rest = value >>> 0;
  do {
    let byte = rest & VARINT_MASK;
    rest >>>= VARINT_BITS;
    if (rest !== 0) {
      byte |= VARINT_MORE;
    }
    bytes.push(byte);
  } while (rest !== 0);
  return Buffer.from(bytes);
}

/** The VarInt at [offset] and where the next field starts, or undefined while it has not all arrived. */
function readVarInt(
  buffer: Buffer,
  offset: number,
): { value: number; next: number } | undefined {
  let value = 0;
  for (let index = 0; offset + index < buffer.length; index++) {
    const byte = buffer.readUInt8(offset + index);
    value |= (byte & VARINT_MASK) << (VARINT_BITS * index);
    if ((byte & VARINT_MORE) === 0) {
      return { value, next: offset + index + 1 };
    }
  }
  return undefined;
}
