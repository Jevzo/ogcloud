import assert from "node:assert/strict";
import { after, test } from "node:test";
import {
  crashLoopGet,
  crashLoopResume,
  groupCreate,
  groupDelete,
} from "@ogcloud/api-client";
import { expectStatus } from "../api.ts";
import { playerName, scenario } from "../suite.ts";
import { eventually } from "../wait.ts";

// A group whose servers never start is backed off and then paused (D-0069's latch), not restarted forever. Five
// failures pause it, with backoffs from 15 s that double, so this takes about five minutes.
const PAUSE_TIMEOUT_MS = 600_000;
const { context } = scenario();
const groupId = `crash-${playerName("")}`;

after(async () => {
  expectStatus(
    await groupDelete(groupId, context.api),
    202,
    `Deleting group ${groupId}`,
  );
});

test("a group whose servers keep crashing is paused until an admin resumes it", async () => {
  expectStatus(
    await groupCreate(
      {
        id: groupId,
        type: "DYNAMIC",
        settings: {
          profileId: "paper-26.3",
          scaling: {
            minOnline: 1,
            maxInstances: 1,
            playersPerServer: 1,
            scaleUpThreshold: 0.8,
            scaleDownThreshold: 0.2,
            cooldownSeconds: 30,
          },
          drainTimeoutSeconds: 0,
          maintenance: false,
          resources: {
            memoryRequest: "512Mi",
            memoryLimit: "512Mi",
            cpuRequest: "100m",
          },
          // The JVM refuses to start with an option it does not know.
          jvmFlags: "-XX:+OgCloudE2eNoSuchOption",
        },
      },
      context.api,
    ),
    201,
    `Creating group ${groupId}`,
  );

  await eventually(
    `${groupId} to be paused`,
    async () => {
      const status = expectStatus(
        await crashLoopGet(groupId, context.api),
        200,
        "Reading the crash loop",
      );
      return status.paused ? status : undefined;
    },
    PAUSE_TIMEOUT_MS,
  );

  const resumed = expectStatus(
    await crashLoopResume(groupId, context.api),
    200,
    `Resuming ${groupId}`,
  );
  assert.equal(resumed.paused, false);
});
