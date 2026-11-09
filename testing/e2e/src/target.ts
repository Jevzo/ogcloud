import { fileURLToPath } from "node:url";

/** The cluster the suite runs against: the minikube dev profile (`mise run dev`) unless OGCLOUD_E2E_* say otherwise. */
export interface Target {
  /** The public origin (D-0098); the API and the login pages live under it. */
  readonly origin: string;
  readonly minecraftHost: string;
  readonly minecraftPort: number;
  readonly kubeContext: string;
  readonly namespace: string;
  /** Holds the internal CA (D-0041), which the suite trusts for the origin's certificate on private clusters. */
  readonly certManagerNamespace: string;
  /** Git-ignored files the seed writes: the owner's login and the suite's machine client. */
  readonly stateDir: string;
}

const REPOSITORY = fileURLToPath(new URL("../../..", import.meta.url));

// Tilt's port-forwards on the dev profile (Tiltfile).
const DEV = {
  OGCLOUD_E2E_ORIGIN: "https://ogcloud.localhost:8443",
  OGCLOUD_E2E_MINECRAFT: "localhost:25565",
  OGCLOUD_E2E_KUBE_CONTEXT: "ogcloud",
  OGCLOUD_E2E_NAMESPACE: "ogcloud",
  OGCLOUD_E2E_CERT_MANAGER_NAMESPACE: "cert-manager",
  OGCLOUD_E2E_STATE: `${REPOSITORY}build/e2e`,
};

export function targetFromEnv(env: NodeJS.ProcessEnv = process.env): Target {
  const value = (name: keyof typeof DEV) => env[name] ?? DEV[name];
  const [host = "", port = ""] = value("OGCLOUD_E2E_MINECRAFT").split(":");
  return {
    origin: value("OGCLOUD_E2E_ORIGIN"),
    minecraftHost: host,
    minecraftPort: Number(port),
    kubeContext: value("OGCLOUD_E2E_KUBE_CONTEXT"),
    namespace: value("OGCLOUD_E2E_NAMESPACE"),
    certManagerNamespace: value("OGCLOUD_E2E_CERT_MANAGER_NAMESPACE"),
    stateDir: value("OGCLOUD_E2E_STATE"),
  };
}
