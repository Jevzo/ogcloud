import assert from "node:assert/strict";
import { test } from "node:test";
import {
  extrapolate,
  paperLogins,
  parseHistogram,
  parseTop,
  percentile,
  shareWithin,
} from "../load/measure.ts";

test("kubectl top's quantities add up in millicores and MiB", () => {
  const usage = parseTop(
    "ogcloud-api-1   250m   512Mi\nogcloud-kafka-0   1   1Gi\nogcloud-tiny   5m   512Ki\n",
  );

  assert.deepEqual(usage, { cpu: 1255, memory: 1536.5 });
});

test("a histogram's label sets are added per bucket", () => {
  const text = [
    "# TYPE ogcloud_gateway_login_resolution_seconds histogram",
    'ogcloud_gateway_login_resolution_seconds_bucket{result="allowed",le="0.05"} 90',
    'ogcloud_gateway_login_resolution_seconds_bucket{result="allowed",le="+Inf"} 100',
    'ogcloud_gateway_login_resolution_seconds_bucket{result="refused",le="0.05"} 9',
    'ogcloud_gateway_login_resolution_seconds_bucket{result="refused",le="+Inf"} 10',
    'ogcloud_gateway_login_resolution_seconds_count{result="allowed"} 100',
    'ogcloud_gateway_login_resolution_seconds_count{result="refused"} 10',
  ].join("\n");

  const histogram = parseHistogram(
    text,
    "ogcloud_gateway_login_resolution_seconds",
  );

  assert.equal(histogram.count, 110);
  assert.equal(histogram.buckets.get(0.05), 99);
  assert.equal(histogram.buckets.get(Infinity), 110);
});

test("only the samples a run took count toward its share", () => {
  const before = { buckets: new Map([[0.05, 10]]), count: 10 };
  const after = { buckets: new Map([[0.05, 109]]), count: 110 };

  assert.equal(shareWithin(before, after, 0.05), 0.99);
  assert.ok(Number.isNaN(shareWithin(after, after, 0.05)));
});

test("percentiles take the nearest rank", () => {
  const values = Array.from({ length: 100 }, (_, index) => index + 1);

  assert.equal(percentile(values, 99), 99);
  assert.equal(percentile(values, 50), 50);
  assert.equal(percentile([7], 99), 7);
});

test("the platform's cost per server and per player scales a run to the target", () => {
  const result = extrapolate(
    {
      baseline: { servers: 3, usage: { cpu: 1000, memory: 4000 } },
      idle: { servers: 33, usage: { cpu: 1300, memory: 4300 } },
      loaded: { players: 500, usage: { cpu: 1800, memory: 4550 } },
    },
    { servers: 303, players: 5000 },
  );

  assert.deepEqual(result.perServer, { cpu: 10, memory: 10 });
  assert.deepEqual(result.perPlayer, { cpu: 1, memory: 0.5 });
  assert.deepEqual(result.atTarget, { cpu: 9000, memory: 9500 });
});

test("Paper's login time runs from a player's pre-login to their placement, across midnight too", () => {
  const log = [
    "[23:59:58 INFO]: UUID of player ld00001 is 5e1b6974-d083-3b55-93e9-370ad9cb9052",
    "[23:59:59 INFO]: UUID of player ld00002 is 5b341691-a954-3271-a36b-4561b1ce0208",
    "[23:59:59 INFO]: ld00001[/10.42.0.1:47560] logged in with entity id 1 at ([minecraft:overworld]9.5, -60.0, -0.5)",
    "[00:00:03 INFO]: ld00002[/10.42.0.1:47561] logged in with entity id 2 at ([minecraft:overworld]9.5, -60.0, -0.5)",
    "[00:00:04 INFO]: UUID of player ld00001 is 5e1b6974-d083-3b55-93e9-370ad9cb9052",
    "[00:00:05 INFO]: ld00001[/10.42.0.1:47562] logged in with entity id 3 at ([minecraft:overworld]9.5, -60.0, -0.5)",
    "[00:00:06 INFO]: UUID of player ld00003 is 6ef97133-299b-3dba-a487-430b5e586505",
  ].join("\n");

  assert.deepEqual(paperLogins(log), [1, 4, 1]);
});
