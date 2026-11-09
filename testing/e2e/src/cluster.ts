import { execFile } from "node:child_process";
import tls from "node:tls";
import { promisify } from "node:util";
import type { Target } from "./target.ts";

const run = promisify(execFile);

/** kubectl against the target's cluster, in OgCloud's namespace unless [namespace] names another. */
export async function kubectl(
  target: Target,
  args: readonly string[],
  namespace = target.namespace,
): Promise<string> {
  const { stdout } = await run("kubectl", [
    "--context",
    target.kubeContext,
    "--namespace",
    namespace,
    ...args,
  ]);
  return stdout;
}

/** One key of a Secret, decoded. */
export async function secretValue(
  target: Target,
  secret: string,
  key: string,
  namespace = target.namespace,
): Promise<string> {
  const escaped = key.replaceAll(".", "\\.");
  const encoded = await kubectl(
    target,
    ["get", "secret", secret, "--output", `jsonpath={.data['${escaped}']}`],
    namespace,
  );
  if (encoded === "") {
    throw new Error(`Secret ${secret} has no ${key}`);
  }
  return Buffer.from(encoded, "base64").toString("utf8");
}

/** Trusts the cluster's internal CA next to the system's, so private clusters' origins verify (D-0041). */
export async function trustInternalCa(target: Target): Promise<void> {
  const ca = await secretValue(
    target,
    "ogcloud-internal-ca",
    "ca.crt",
    target.certManagerNamespace,
  );
  tls.setDefaultCACertificates([...tls.getCACertificates("default"), ca]);
}
