import assert from "node:assert/strict";
import { test } from "node:test";
import {
  extrapolate,
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
