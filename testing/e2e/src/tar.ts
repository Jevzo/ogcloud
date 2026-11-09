import { gzipSync } from "node:zlib";

const BLOCK = 512;

/** A gzipped ustar archive of regular files, as the API takes templates (D-0103). */
export function tarGz(files: Record<string, string>): Uint8Array<ArrayBuffer> {
  const blocks: Buffer[] = [];
  for (const [path, text] of Object.entries(files)) {
    const content = Buffer.from(text, "utf8");
    blocks.push(header(path, content.length), pad(content));
  }
  blocks.push(Buffer.alloc(BLOCK * 2));
  return new Uint8Array(gzipSync(Buffer.concat(blocks)));
}

function header(path: string, size: number): Buffer {
  if (Buffer.byteLength(path) > 100) {
    throw new Error(`${path} is longer than a ustar name`);
  }
  const block = Buffer.alloc(BLOCK);
  block.write(path, 0, "utf8");
  block.write("0000644\0", 100);
  block.write("0000000\0", 108);
  block.write("0000000\0", 116);
  block.write(`${size.toString(8).padStart(11, "0")}\0`, 124);
  block.write("00000000000\0", 136);
  block.write("        ", 148);
  block.write("0", 156);
  block.write("ustar\0", 257);
  block.write("00", 263);
  const checksum = block.reduce((sum, byte) => sum + byte, 0);
  block.write(`${checksum.toString(8).padStart(6, "0")}\0 `, 148);
  return block;
}

function pad(content: Buffer): Buffer {
  const rest = content.length % BLOCK;
  return rest === 0
    ? content
    : Buffer.concat([content, Buffer.alloc(BLOCK - rest)]);
}
