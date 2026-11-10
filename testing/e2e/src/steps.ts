/** A step that puts something back: what it does, for its error, and the doing. */
export type Step = readonly [what: string, run: () => Promise<unknown>];

/** Runs every step, also after one fails, and returns the failures, each naming its step. */
export async function everyStep(steps: readonly Step[]): Promise<Error[]> {
  const failures: Error[] = [];
  for (const [what, run] of steps) {
    try {
      await run();
    } catch (error) {
      failures.push(
        new Error(`${what}: ${(error as Error).message}`, { cause: error }),
      );
    }
  }
  return failures;
}
