import * as os from "node:os";
import * as path from "node:path";

import type { CliCommand, ComponentName, RequiredCommand } from "./types";

export const COMMAND_DOCS: Record<RequiredCommand, string> = {
    kubectl: "https://kubernetes.io/docs/tasks/tools/",
    helm: "https://helm.sh/docs/intro/install/",
    npm: "https://docs.npmjs.com/downloading-and-installing-node-js-and-npm",
    npx: "https://docs.npmjs.com/cli/v11/commands/npx",
    node: "https://nodejs.org/en/download",
};

export const REPO_OWNER = "Jevzo";
export const REPO_NAME = "ogcloud";
export const REPO_REF = "main";
export const REMOTE_HELM_PATH = "helm";
export const HELM_REPO_URL = `https://github.com/${REPO_OWNER}/${REPO_NAME}/tree/${REPO_REF}/helm`;

export const RELEASES = {
    infra: "ogcloud-infra",
    platform: "ogcloud-platform",
    dashboard: "ogcloud-dashboard",
} as const;

export const COLORS = {
    reset: "\u001b[0m",
    bold: "\u001b[1m",
    dim: "\u001b[2m",
    red: "\u001b[31m",
    green: "\u001b[32m",
    yellow: "\u001b[33m",
    blue: "\u001b[34m",
    magenta: "\u001b[35m",
    cyan: "\u001b[36m",
} as const;

export const STATE_ROOT = path.join(os.homedir(), ".ogcloud-setup");
export const NETWORKS_ROOT = path.join(STATE_ROOT, "networks");
export const CACHE_ROOT = path.join(STATE_ROOT, "cache");
export const CACHE_SCOPE = `${REPO_OWNER}-${REPO_NAME}-${REPO_REF}`;
export const HELM_CACHE_ROOT = path.join(CACHE_ROOT, CACHE_SCOPE);
export const HELM_CACHE_DIR = path.join(HELM_CACHE_ROOT, REMOTE_HELM_PATH);
export const CACHE_META_FILE = path.join(HELM_CACHE_ROOT, "meta.json");
export const STATE_FILE = path.join(STATE_ROOT, "state.json");

export const BACKING_SERVICES = ["mongodb", "redis", "minio", "kafka"] as const;
export const COMPONENTS: readonly ComponentName[] = [
    "dashboard",
    "api",
    "loadbalancer",
    "controller",
    "template-loader",
];
export const COMMAND_ORDER: readonly CliCommand[] = [
    "generate-config",
    "deploy",
    "update",
    "destroy",
];
export const NETWORK_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;
export const DEFAULT_IMAGE_VERSION = "1.3.0";
export const DEFAULT_INGRESS_CLASS_NAME = "nginx";
export const FIXED_IMAGE_REPOSITORIES = {
    api: "ogwarsdev/api",
    controller: "ogwarsdev/controller",
    loadbalancer: "ogwarsdev/loadbalancer",
    dashboard: "ogwarsdev/dashboard",
} as const;
