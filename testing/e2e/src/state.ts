import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { MachineClient, UserLogin } from "./login.ts";
import type { Target } from "./target.ts";

/** What the seed leaves for the suite in the git-ignored state directory: secrets, readable only by this user. */
export interface SeedState {
  readonly owner: UserLogin;
  readonly client?: MachineClient;
}

const FILE = "seed.json";

export async function loadState(
  target: Target,
): Promise<SeedState | undefined> {
  try {
    return JSON.parse(
      await readFile(join(target.stateDir, FILE), "utf8"),
    ) as SeedState;
  } catch (error) {
    if (error instanceof Error && "code" in error && error.code === "ENOENT") {
      return undefined;
    }
    throw error;
  }
}

export async function saveState(
  target: Target,
  state: SeedState,
): Promise<void> {
  await mkdir(target.stateDir, { recursive: true, mode: 0o700 });
  await writeFile(
    join(target.stateDir, FILE),
    `${JSON.stringify(state, null, 2)}\n`,
    { mode: 0o600 },
  );
}
