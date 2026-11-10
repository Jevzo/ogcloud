import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ApiError,
  privacyErasePlayer,
  privacyExportPlayer,
} from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { join, leave } from "../bots.ts";
import { onlinePlayer, playerName, scenario } from "../suite.ts";
import { eventually } from "../wait.ts";

// Privacy erasure (D-0049, D-0107): once the player has left, erasing them leaves nothing to export.
const { context } = scenario();

test("an erased player leaves nothing to export", async () => {
  const name = playerName("gone");
  const player = await join(context.target, name);
  const { uuid } = await eventually(`${name} to be online`, () =>
    onlinePlayer(context.api, name),
  );
  leave(player);
  await eventually(`${name} to be offline`, async () =>
    (await onlinePlayer(context.api, name)) === undefined ? true : undefined,
  );

  const stored = expectStatus(
    await privacyExportPlayer(uuid, context.api),
    200,
    `Exporting ${name}`,
  );
  assert.equal(stored.player.name, name);
  expectStatus(
    await privacyErasePlayer(uuid, context.api),
    204,
    `Erasing ${name}`,
  );

  await assert.rejects(
    privacyExportPlayer(uuid, context.api),
    (error: unknown) => error instanceof ApiError && error.status === 404,
    "an export after the erasure found the player",
  );
});
