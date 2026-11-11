import { execFileSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, relative } from "node:path";

// orval writes into a sibling of src/generated, so its relative imports come out the same, and src/generated is then
// brought in line file by file: orval itself keeps a removed schema's export in the shared model index, and emptying
// src/generated first would race the builds that read it while `mise run check` runs.
const CURRENT = "src/generated";
const NEXT = "src/.generated-next";

function files(root: string): string[] {
  if (!existsSync(root)) return [];
  return readdirSync(root, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => relative(root, join(entry.parentPath, entry.name)));
}

rmSync(NEXT, { recursive: true, force: true });
try {
  execFileSync(
    join("node_modules", ".bin", "orval"),
    ["--config", "orval.config.ts"],
    { stdio: "inherit", env: { ...process.env, OGCLOUD_API_CLIENT_OUT: NEXT } },
  );
  const made = new Set(files(NEXT));
  for (const file of made) {
    const content = readFileSync(join(NEXT, file));
    const target = join(CURRENT, file);
    if (existsSync(target) && readFileSync(target).equals(content)) continue;
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, content);
  }
  for (const file of files(CURRENT)) {
    if (!made.has(file)) rmSync(join(CURRENT, file));
  }
} finally {
  rmSync(NEXT, { recursive: true, force: true });
}
