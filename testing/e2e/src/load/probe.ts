import { spawn } from "node:child_process";
import { once } from "node:events";
import { kubectl } from "../cluster.ts";
import type { Target } from "../target.ts";
import { eventually } from "../wait.ts";
import {
  type Histogram,
  parseHistogram,
  parseTop,
  type Usage,
} from "./measure.ts";

// The gateway's admin port serves its metrics (pkg/service); port-forwards reach each replica past NetworkPolicy.
const GATEWAY_ADMIN_PORT = 8081;
const GATEWAY_SELECTOR = "app.kubernetes.io/component=gateway";
const LOGIN_HISTOGRAM = "ogcloud_gateway_login_resolution_seconds";
const FORWARDING = /Forwarding from 127\.0\.0\.1:(\d+)/;
// A port-forward through a loaded node can drop its first connection, so a scrape is tried again.
const SCRAPE_TIMEOUT_MS = 60_000;

/** What every pod in the namespace uses right now, from the metrics server. */
export async function usage(target: Target, namespace: string): Promise<Usage> {
  return parseTop(
    await kubectl(target, ["top", "pods", "--no-headers"], namespace),
  );
}

/** The login-resolution histogram of every gateway replica, added together (D-0175). */
export async function loginHistogram(target: Target): Promise<Histogram> {
  const pods = (
    await kubectl(target, [
      "get",
      "pods",
      "--selector",
      GATEWAY_SELECTOR,
      "--output",
      "jsonpath={.items[*].metadata.name}",
    ])
  )
    .split(" ")
    .filter((pod) => pod !== "");
  if (pods.length === 0) {
    throw new Error(`no gateway pod matches ${GATEWAY_SELECTOR}`);
  }
  const buckets = new Map<number, number>();
  let count = 0;
  for (const pod of pods) {
    const metrics = await eventually(
      `${pod}'s metrics`,
      () => metricsOf(target, pod),
      SCRAPE_TIMEOUT_MS,
    );
    const histogram = parseHistogram(metrics, LOGIN_HISTOGRAM);
    count += histogram.count;
    for (const [bound, value] of histogram.buckets) {
      buckets.set(bound, (buckets.get(bound) ?? 0) + value);
    }
  }
  return { buckets, count };
}

async function metricsOf(target: Target, pod: string): Promise<string> {
  const forward = spawn("kubectl", [
    "--context",
    target.kubeContext,
    "--namespace",
    target.namespace,
    "port-forward",
    `pod/${pod}`,
    `:${GATEWAY_ADMIN_PORT}`,
  ]);
  let said = "";
  forward.stderr.on("data", (chunk: Buffer) => {
    said += chunk.toString();
  });
  try {
    const port = await new Promise<string>((resolve, reject) => {
      forward.stdout.on("data", (chunk: Buffer) => {
        const match = chunk.toString().match(FORWARDING);
        if (match?.[1] !== undefined) {
          resolve(match[1]);
        }
      });
      forward.once("exit", (code) =>
        reject(new Error(`the port-forward to ${pod} ended with ${code}`)),
      );
    });
    const response = await fetch(`http://127.0.0.1:${port}/metrics`).catch(
      (error: Error) => {
        throw new Error(
          `${pod}'s metrics: ${error.message}; kubectl said: ${said.trim()}`,
          { cause: error },
        );
      },
    );
    if (!response.ok) {
      throw new Error(`${pod}'s metrics answered ${response.status}`);
    }
    return await response.text();
  } finally {
    if (forward.exitCode === null) {
      const exited = once(forward, "exit");
      forward.kill();
      await exited;
    }
  }
}
