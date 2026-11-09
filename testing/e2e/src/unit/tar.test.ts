import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { tarGz } from "../tar.ts";

test("tar reads the archive back with every file", () => {
  const dir = mkdtempSync(join(tmpdir(), "ogcloud-tar-"));
  const archive = join(dir, "template.tar.gz");
  writeFileSync(
    archive,
    tarGz({
      "server.properties": "motd=e2e\n",
      "plugins/marker.txt": "x".repeat(600),
    }),
  );

  assert.deepEqual(
    execFileSync("tar", ["-tzf", archive], { encoding: "utf8" })
      .trim()
      .split("\n")
      .sort(),
    ["plugins/marker.txt", "server.properties"],
  );
  execFileSync("tar", ["-xzf", archive, "-C", dir]);
  assert.equal(
    readFileSync(join(dir, "server.properties"), "utf8"),
    "motd=e2e\n",
  );
  assert.equal(
    readFileSync(join(dir, "plugins/marker.txt"), "utf8").length,
    600,
  );
});
