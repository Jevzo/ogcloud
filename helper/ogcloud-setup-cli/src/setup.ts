#!/usr/bin/env node

import * as fs from "node:fs/promises";
import * as fsSync from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import * as https from "node:https";
import * as readline from "node:readline";
import * as nodeCrypto from "node:crypto";
import { spawnSync, type SpawnSyncReturns } from "node:child_process";

type CliCommand = "generate-config" | "deploy" | "update" | "destroy";
type BackingMode = "managed" | "external";
type ComponentName = "dashboard" | "api" | "loadbalancer" | "controller";
type ValuesFileKey = "infra" | "platform" | "dashboard";
type JsonMap = Record<string, unknown>;

type Choice<T = string> = {
    label: string;
    value: T;
    description?: string;
};

type ParsedArgs = {
    help: boolean;
    refreshHelm: boolean;
    withoutBacking: boolean;
    interactive: boolean;
    context: string | null;
    command: CliCommand | null;
    network: string | null;
    component: string | null;
    imageVersion: string | null;
};

type RunCommandOptions = {
    stdio?: "pipe" | "inherit";
    cwd?: string;
    allowFailure?: boolean;
};

type AskInputOptions = {
    defaultValue?: unknown;
    required?: boolean;
    validator?: (value: string) => true | string;
};

type StateFile = {
    lastNetwork: string;
    lastContext: string;
    networks: Record<string, { namespace: string; configPath: string; updatedAt: string }>;
};

type NetworkConnections = {
    mongodbUri: string;
    redisHost: string;
    redisPort: string;
    kafkaBrokers: string;
    minioEndpoint: string;
    apiUrl: string;
};

type ImageTags = {
    api: string;
    controller: string;
    loadbalancer: string;
    dashboard: string;
};

type NetworkValues = Record<ValuesFileKey, JsonMap>;
type NetworkFiles = Record<ValuesFileKey, string>;

type NetworkConfig = {
    schemaVersion: number;
    network: string;
    namespace: string;
    deployBackingServices: boolean;
    deployDashboard: boolean;
    backingMode: BackingMode;
    ingressEnabled: boolean;
    ingressClassName: string;
    apiDomain: string;
    dashboardDomain: string;
    loadbalancerDomain: string;
    imageTags: ImageTags;
    apiEmail: string;
    apiPassword: string;
    podForwardingSecret: string;
    jwtSecret: string;
    corsAllowedOrigin: string;
    minioAccessKey: string;
    minioSecretKey: string;
    connections: NetworkConnections;
    apiBaseUrl: string;
    values: NetworkValues;
    files: NetworkFiles;
    updatedAt: string;
};

type ExistingNetworkConfig = Partial<NetworkConfig> & {
    imageTags?: Partial<ImageTags>;
    connections?: Partial<NetworkConnections>;
    values?: Partial<NetworkValues>;
    files?: Partial<NetworkFiles>;
};

type ValuesPaths = {
    config: string;
    infra: string;
    platform: string;
    dashboard: string;
};

type RequiredCommand = "kubectl" | "helm" | "npm" | "npx" | "node";

const COMMAND_DOCS: Record<RequiredCommand, string> = {
    kubectl: "https://kubernetes.io/docs/tasks/tools/",
    helm: "https://helm.sh/docs/intro/install/",
    npm: "https://docs.npmjs.com/downloading-and-installing-node-js-and-npm",
    npx: "https://docs.npmjs.com/cli/v11/commands/npx",
    node: "https://nodejs.org/en/download",
};

const REPO_OWNER = "Jevzo";
const REPO_NAME = "ogcloud";
const REPO_REF = "dev";
const REMOTE_HELM_PATH = "helm";
const HELM_REPO_URL = `https://github.com/${REPO_OWNER}/${REPO_NAME}/tree/${REPO_REF}/helm`;

const RELEASES = {
    infra: "ogcloud-infra",
    platform: "ogcloud-platform",
    dashboard: "ogcloud-dashboard",
};

const COLORS = {
    reset: "\u001b[0m",
    bold: "\u001b[1m",
    dim: "\u001b[2m",
    red: "\u001b[31m",
    green: "\u001b[32m",
    yellow: "\u001b[33m",
    blue: "\u001b[34m",
    magenta: "\u001b[35m",
    cyan: "\u001b[36m",
};

const STATE_ROOT = path.join(os.homedir(), ".ogcloud-setup");
const NETWORKS_ROOT = path.join(STATE_ROOT, "networks");
const CACHE_ROOT = path.join(STATE_ROOT, "cache");
const CACHE_SCOPE = `${REPO_OWNER}-${REPO_NAME}-${REPO_REF}`;
const HELM_CACHE_ROOT = path.join(CACHE_ROOT, CACHE_SCOPE);
const HELM_CACHE_DIR = path.join(HELM_CACHE_ROOT, REMOTE_HELM_PATH);
const CACHE_META_FILE = path.join(HELM_CACHE_ROOT, "meta.json");
const STATE_FILE = path.join(STATE_ROOT, "state.json");

const BACKING_SERVICES = ["mongodb", "redis", "minio", "kafka"];
const COMPONENTS: ComponentName[] = ["dashboard", "api", "loadbalancer", "controller"];
const NETWORK_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;
const DEFAULT_IMAGE_VERSION = "1.2.3"; // FIXME: Update before merge
const DEFAULT_INGRESS_CLASS_NAME = "nginx";
const FIXED_IMAGE_REPOSITORIES = {
    api: "ogwarsdev/api",
    controller: "ogwarsdev/controller",
    loadbalancer: "ogwarsdev/loadbalancer",
    dashboard: "ogwarsdev/dashboard",
};

function color(text: string, ...codes: string[]): string {
    return `${codes.join("")}${text}${COLORS.reset}`;
}

function heading(message: string): void {
    console.log(color(message, COLORS.bold, COLORS.cyan));
}

function info(message: string): void {
    console.log(`${color("INFO", COLORS.bold, COLORS.cyan)} ${message}`);
}

function warn(message: string): void {
    console.log(`${color("WARN", COLORS.bold, COLORS.yellow)} ${message}`);
}

function ok(message: string): void {
    console.log(`${color("OK", COLORS.bold, COLORS.green)} ${message}`);
}

function fail(message: string): void {
    console.error(`${color("ERROR", COLORS.bold, COLORS.red)} ${message}`);
}

function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function parseArgs(argv: string[]): ParsedArgs {
    const parsed: ParsedArgs = {
        help: false,
        refreshHelm: false,
        withoutBacking: false,
        interactive: false,
        context: null,
        command: null,
        network: null,
        component: null,
        imageVersion: null,
    };

    const consumeOptionalValue = (index: number): string | null => {
        const value = argv[index + 1];
        if (!value || value.startsWith("-")) {
            return null;
        }
        return value;
    };

    const setCommand = (command: CliCommand): void => {
        if (parsed.command && parsed.command !== command) {
            throw new Error(
                `Multiple commands provided: "${parsed.command}" and "${command}". Use only one command.`,
            );
        }
        parsed.command = command;
    };

    const setNetwork = (network: string | null): void => {
        if (!network) {
            return;
        }
        if (parsed.network && parsed.network !== network) {
            throw new Error(`Conflicting network values: "${parsed.network}" and "${network}".`);
        }
        parsed.network = network;
    };

    for (let i = 0; i < argv.length; i += 1) {
        const arg = argv[i];
        const next = () => {
            const value = consumeOptionalValue(i);
            if (value != null) {
                i += 1;
            }
            return value;
        };

        switch (arg) {
            case "--help":
            case "-h":
                parsed.help = true;
                continue;
            case "--interactive":
                parsed.interactive = true;
                continue;
            case "--refresh-helm":
                parsed.refreshHelm = true;
                continue;
            case "--without-backing":
                parsed.withoutBacking = true;
                continue;
            case "--context":
                parsed.context = next();
                continue;
            case "--generate-config":
                setCommand("generate-config");
                setNetwork(next());
                continue;
            case "--deploy":
                setCommand("deploy");
                setNetwork(next());
                continue;
            case "--update":
                setCommand("update");
                setNetwork(next());
                parsed.component = next();
                parsed.imageVersion = next();
                continue;
            case "--destroy":
                setCommand("destroy");
                setNetwork(next());
                continue;
            default:
                if (arg.startsWith("-")) {
                    throw new Error(`Unknown argument: ${arg}`);
                }

                if (!parsed.network) {
                    parsed.network = arg;
                    continue;
                }

                if (parsed.command === "update" && !parsed.component) {
                    parsed.component = arg;
                    continue;
                }

                if (parsed.command === "update" && !parsed.imageVersion) {
                    parsed.imageVersion = arg;
                    continue;
                }

                throw new Error(`Unexpected positional argument: ${arg}`);
        }
    }

    return parsed;
}

function printHelp(): void {
    console.log(`
${color("OgCloud Setup CLI", COLORS.bold, COLORS.cyan)}
Quickstart lifecycle utility for OgCloud Helm deployment.

${color("Usage", COLORS.bold)}
  ogcloud-setup --generate-config <network_name>
  ogcloud-setup --deploy <network_name>
  ogcloud-setup --update <network_name> <component> <image_version>
  ogcloud-setup --destroy <network_name>

${color("Interactive mode", COLORS.bold)}
  ogcloud-setup
  npx @ogcloud/setup ogwars

${color("npx examples", COLORS.bold)}
  npx @ogcloud/setup --generate-config ogwars
  npx @ogcloud/setup --deploy ogwars
  npx @ogcloud/setup

${color("Flags", COLORS.bold)}
  --context <name>       Use a specific kube context before prompting
  --refresh-helm         Force fresh download of remote Helm files
  --without-backing      Deprecated; ignored. Backing deployment is now stored in config
  --interactive          Open guided flow
  --help                 Show this help
`);
}

function resolveExecutable(command: string): string {
    if (process.platform === "win32" && (command === "npm" || command === "npx")) {
        return `${command}.cmd`;
    }
    return command;
}

function getCommandDocs(command: string): string | undefined {
    if (Object.prototype.hasOwnProperty.call(COMMAND_DOCS, command)) {
        return COMMAND_DOCS[command as RequiredCommand];
    }
    return undefined;
}

function runCommand(
    command: string,
    args: string[],
    options: RunCommandOptions = {},
): SpawnSyncReturns<string> {
    const executable = resolveExecutable(command);
    const result = spawnSync(executable, args, {
        encoding: "utf8",
        stdio: options.stdio || "pipe",
        cwd: options.cwd || process.cwd(),
    });

    if (result.error) {
        const runError = result.error as NodeJS.ErrnoException;
        if (runError.code === "ENOENT") {
            const docs = getCommandDocs(command);
            const docsSuffix = docs ? `\nInstall/docs: ${docs}` : "";
            throw new Error(`Command not found: ${command}${docsSuffix}`);
        }
        throw runError;
    }

    if (!options.allowFailure && result.status !== 0) {
        const stderr = (result.stderr || "").trim();
        const stdout = (result.stdout || "").trim();
        const detail = stderr || stdout || "Unknown command failure";
        throw new Error(`${command} ${args.join(" ")} failed: ${detail}`);
    }

    return result;
}

function commandExists(command: string, args: string[] = ["--help"]): boolean {
    try {
        const executable = resolveExecutable(command);
        const result = spawnSync(executable, args, {
            encoding: "utf8",
            stdio: "pipe",
        });
        return !(result.error && (result.error as NodeJS.ErrnoException).code === "ENOENT");
    } catch {
        return false;
    }
}

function sanitizeNetworkName(network: string | null | undefined): string {
    if (!network) {
        return "";
    }
    return network.trim().toLowerCase();
}

function validateNetworkName(network: string): boolean {
    return NETWORK_PATTERN.test(network);
}

function sanitizeComponent(component: string | null | undefined): string {
    if (!component) {
        return "";
    }
    return component.trim().toLowerCase();
}

function isComponentName(component: string): component is ComponentName {
    return COMPONENTS.includes(component as ComponentName);
}

function ensureDependencies(): void {
    const required: Array<{ command: RequiredCommand; args: string[] }> = [
        { command: "kubectl", args: ["version", "--client=true"] },
        { command: "helm", args: ["version"] },
        { command: "npm", args: ["--version"] },
        { command: "npx", args: ["--version"] },
        { command: "node", args: ["--version"] },
    ];

    const missing = required
        .filter((entry) => !commandExists(entry.command, entry.args))
        .map((entry) => entry.command);

    if (missing.length > 0) {
        const helpLines = missing
            .map((name) => {
                const docs = COMMAND_DOCS[name];
                return docs ? `- ${name}: ${docs}` : `- ${name}`;
            })
            .join("\n");
        throw new Error(`Missing required dependencies: ${missing.join(", ")}\n${helpLines}`);
    }
}

let promptInterface: readline.Interface | null = null;
let promptInputClosed = false;

function getPromptInterface(): readline.Interface {
    if (promptInputClosed) {
        throw new Error("Standard input is closed. Interactive prompts cannot continue.");
    }

    if (!promptInterface) {
        promptInterface = readline.createInterface({
            input: process.stdin,
            output: process.stdout,
        });
        promptInterface.once("close", () => {
            promptInterface = null;
            promptInputClosed = true;
        });
    }

    return promptInterface;
}

function closePromptInterface(): void {
    if (promptInterface) {
        promptInterface.close();
        promptInterface = null;
    }
}

function question(query: string): Promise<string> {
    return new Promise<string>((resolve, reject) => {
        let rl: readline.Interface;
        try {
            rl = getPromptInterface();
        } catch (error) {
            reject(error);
            return;
        }

        let settled = false;
        const onClose = () => {
            if (settled) {
                return;
            }
            settled = true;
            reject(new Error("Standard input closed before the prompt was answered."));
        };

        rl.once("close", onClose);
        rl.question(query, (answer) => {
            if (settled) {
                return;
            }
            settled = true;
            rl.removeListener("close", onClose);
            resolve(answer);
        });
    });
}

async function askInput(message: string, options: AskInputOptions = {}): Promise<string> {
    const defaultValue = options.defaultValue == null ? "" : String(options.defaultValue);
    const required = options.required !== false;
    const validator = options.validator;
    while (true) {
        const suffix = defaultValue ? color(` [${defaultValue}]`, COLORS.dim) : "";
        const raw = await question(`${color(">", COLORS.cyan)} ${message}${suffix}: `);
        const value = raw.trim() || defaultValue;
        if (!value && required) {
            warn("A value is required.");
            continue;
        }
        if (validator) {
            const validation = validator(value);
            if (validation !== true) {
                warn(validation);
                continue;
            }
        }
        return value;
    }
}

async function askSelect<T>(
    message: string,
    choices: Array<Choice<T>>,
    defaultIndex = 0,
): Promise<T> {
    if (!Array.isArray(choices) || choices.length === 0) {
        throw new Error("askSelect requires at least one choice.");
    }

    console.log(`${color(">", COLORS.cyan)} ${message}`);
    for (let i = 0; i < choices.length; i += 1) {
        const choice = choices[i];
        const label = `${i + 1}) ${choice.label}`;
        if (choice.description) {
            console.log(
                `  ${color(label, COLORS.magenta)} ${color(`- ${choice.description}`, COLORS.dim)}`,
            );
        } else {
            console.log(`  ${color(label, COLORS.magenta)}`);
        }
    }

    const value = await askInput("Choose", {
        defaultValue: String(defaultIndex + 1),
        validator: (inputValue) => {
            const index = Number.parseInt(inputValue, 10);
            if (!Number.isFinite(index) || index < 1 || index > choices.length) {
                return `Enter a number between 1 and ${choices.length}.`;
            }
            return true;
        },
    });
    const index = Number.parseInt(value, 10) - 1;
    return choices[index].value;
}

async function askConfirm(message: string, defaultYes = true): Promise<boolean> {
    const value = await askSelect(
        message,
        [
            { label: "Yes", value: true },
            { label: "No", value: false },
        ],
        defaultYes ? 0 : 1,
    );
    return Boolean(value);
}

async function readJsonFile<T>(filePath: string, fallbackValue: T): Promise<T> {
    try {
        const raw = await fs.readFile(filePath, "utf8");
        return JSON.parse(raw) as T;
    } catch (error) {
        const fsError = error as NodeJS.ErrnoException;
        if (fsError.code === "ENOENT") {
            return fallbackValue;
        }
        throw error;
    }
}

async function saveJsonFile(filePath: string, value: unknown): Promise<void> {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    await fs.writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function yamlScalar(value: unknown): string {
    if (value === null || value === undefined) {
        return "null";
    }
    if (typeof value === "boolean") {
        return value ? "true" : "false";
    }
    if (typeof value === "number") {
        return Number.isFinite(value) ? String(value) : "null";
    }
    const text = String(value);
    const lower = text.toLowerCase();
    const shouldQuote =
        text.length === 0 ||
        /^\d+$/.test(text) ||
        ["null", "true", "false", "~"].includes(lower) ||
        /[:#[\]{},&*!?|>'"%@`-]/.test(text) ||
        /\s/.test(text);
    return shouldQuote ? JSON.stringify(text) : text;
}

function isObject(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value);
}

function renderYamlLines(value: unknown, indent: number): string[] {
    const pad = "  ".repeat(indent);

    if (Array.isArray(value)) {
        if (value.length === 0) {
            return [`${pad}[]`];
        }
        const lines = [];
        for (const item of value) {
            if (Array.isArray(item) || isObject(item)) {
                lines.push(`${pad}-`);
                lines.push(...renderYamlLines(item, indent + 1));
            } else {
                lines.push(`${pad}- ${yamlScalar(item)}`);
            }
        }
        return lines;
    }

    if (isObject(value)) {
        const keys = Object.keys(value);
        if (keys.length === 0) {
            return [`${pad}{}`];
        }
        const lines = [];
        for (const key of keys) {
            const item = value[key];
            if (Array.isArray(item) && item.length === 0) {
                lines.push(`${pad}${key}: []`);
            } else if (isObject(item) && Object.keys(item).length === 0) {
                lines.push(`${pad}${key}: {}`);
            } else if (Array.isArray(item) || isObject(item)) {
                lines.push(`${pad}${key}:`);
                lines.push(...renderYamlLines(item, indent + 1));
            } else {
                lines.push(`${pad}${key}: ${yamlScalar(item)}`);
            }
        }
        return lines;
    }

    return [`${pad}${yamlScalar(value)}`];
}

function toYaml(value: unknown): string {
    return `${renderYamlLines(value, 0).join("\n")}\n`;
}

function setDeepValue(target: JsonMap, pathParts: string[], value: unknown): void {
    let current: JsonMap = target;
    for (let i = 0; i < pathParts.length - 1; i += 1) {
        const key = pathParts[i];
        const nextValue = current[key];
        if (!isObject(nextValue)) {
            const nested: JsonMap = {};
            current[key] = nested;
            current = nested;
            continue;
        }
        current = nextValue;
    }
    current[pathParts[pathParts.length - 1]] = value;
}

function encodeRepoPath(repoPath: string): string {
    return repoPath
        .split("/")
        .filter(Boolean)
        .map((segment) => encodeURIComponent(segment))
        .join("/");
}

function httpsGetBuffer(
    url: string,
    headers: Record<string, string> = {},
    redirectCount = 0,
): Promise<Buffer> {
    return new Promise<Buffer>((resolve, reject) => {
        const request = https.get(
            url,
            {
                headers: {
                    "User-Agent": "ogcloud-setup-cli",
                    Accept: "application/vnd.github+json",
                    ...headers,
                },
            },
            (response) => {
                const statusCode = response.statusCode || 0;

                if ([301, 302, 307, 308].includes(statusCode) && response.headers.location) {
                    if (redirectCount >= 10) {
                        response.resume();
                        reject(new Error(`Too many redirects while requesting ${url}`));
                        return;
                    }

                    const redirectTarget = Array.isArray(response.headers.location)
                        ? response.headers.location[0]
                        : response.headers.location;
                    const redirectUrl = new URL(redirectTarget, url).toString();
                    response.resume();
                    resolve(httpsGetBuffer(redirectUrl, headers, redirectCount + 1));
                    return;
                }

                const chunks: Buffer[] = [];
                response.on("data", (chunk) => chunks.push(chunk));
                response.on("end", () => {
                    const body = Buffer.concat(chunks);
                    if (statusCode < 200 || statusCode >= 300) {
                        reject(
                            new Error(
                                `HTTP ${statusCode} for ${url}: ${body.toString("utf8").slice(0, 240)}`,
                            ),
                        );
                        return;
                    }
                    resolve(body);
                });
            },
        );

        request.on("error", (error) => reject(error));
    });
}

async function fetchJson<T>(url: string): Promise<T> {
    const buffer = await httpsGetBuffer(url);
    return JSON.parse(buffer.toString("utf8")) as T;
}

async function downloadGithubDirectory(repoPath: string, localPath: string): Promise<void> {
    await fs.mkdir(localPath, { recursive: true });
    const apiUrl = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/contents/${encodeRepoPath(repoPath)}?ref=${REPO_REF}`;
    const entries = await fetchJson<unknown>(apiUrl);

    if (!Array.isArray(entries)) {
        throw new Error(`Unexpected GitHub API response while reading ${repoPath}.`);
    }

    for (const entryRaw of entries) {
        if (!isObject(entryRaw)) {
            throw new Error(`Unexpected directory entry while reading ${repoPath}.`);
        }

        const entryName = typeof entryRaw.name === "string" ? entryRaw.name : "";
        const entryType = typeof entryRaw.type === "string" ? entryRaw.type : "";
        if (!entryName || !entryType) {
            throw new Error(`Invalid directory entry while reading ${repoPath}.`);
        }

        const targetPath = path.join(localPath, entryName);
        if (entryType === "dir") {
            await downloadGithubDirectory(`${repoPath}/${entryName}`, targetPath);
            continue;
        }

        if (entryType === "file") {
            const downloadUrl =
                typeof entryRaw.download_url === "string" ? entryRaw.download_url : "";
            if (!downloadUrl) {
                throw new Error(`Missing download URL for ${repoPath}/${entryName}.`);
            }
            const content = await httpsGetBuffer(downloadUrl, {
                Accept: "application/octet-stream",
            });
            await fs.writeFile(targetPath, content);
        }
    }
}

function helmCacheIsReady(): boolean {
    const chartFiles = [
        path.join(HELM_CACHE_DIR, "ogcloud-infra", "Chart.yaml"),
        path.join(HELM_CACHE_DIR, "ogcloud-platform", "Chart.yaml"),
        path.join(HELM_CACHE_DIR, "ogcloud-dashboard", "Chart.yaml"),
    ];
    return chartFiles.every((filePath) => fsSync.existsSync(filePath));
}

async function ensureHelmCache(refresh: boolean): Promise<string> {
    if (!refresh && helmCacheIsReady()) {
        info(`Using cached Helm files: ${HELM_CACHE_DIR}`);
        return HELM_CACHE_DIR;
    }

    info(`Downloading Helm files from ${HELM_REPO_URL}`);
    await fs.rm(HELM_CACHE_ROOT, { recursive: true, force: true });
    await fs.mkdir(HELM_CACHE_ROOT, { recursive: true });
    await downloadGithubDirectory(REMOTE_HELM_PATH, HELM_CACHE_DIR);
    await saveJsonFile(CACHE_META_FILE, {
        source: HELM_REPO_URL,
        downloadedAt: new Date().toISOString(),
    });
    ok(`Helm files downloaded to ${HELM_CACHE_DIR}`);
    return HELM_CACHE_DIR;
}

function getCurrentContext(): string {
    const result = runCommand("kubectl", ["config", "current-context"], { allowFailure: true });
    return (result.stdout || "").trim();
}

function getContexts(): string[] {
    const result = runCommand("kubectl", ["config", "get-contexts", "-o", "name"], {
        allowFailure: true,
    });
    if (result.status !== 0) {
        return [];
    }
    return (result.stdout || "")
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean);
}

async function ensureClusterContext(requestedContext?: string | null): Promise<string> {
    if (requestedContext) {
        info(`Switching context to ${requestedContext}`);
        runCommand("kubectl", ["config", "use-context", requestedContext], { stdio: "inherit" });
    }

    if (!process.stdin.isTTY) {
        const context = getCurrentContext();
        if (!context) {
            throw new Error(
                "No active kubectl context and no TTY available for interactive selection.",
            );
        }
        warn(`Non-interactive terminal detected. Using context: ${context}`);
        return context;
    }

    while (true) {
        const current = getCurrentContext();
        if (!current) {
            throw new Error("No active kubectl context found. Configure kubeconfig and try again.");
        }

        const infoResult = runCommand("kubectl", ["cluster-info"], { allowFailure: true });
        console.log(color(`\nCurrent context: ${current}`, COLORS.bold, COLORS.blue));
        if (infoResult.status === 0) {
            console.log(color((infoResult.stdout || "").trim(), COLORS.dim));
        } else {
            warn("Unable to query cluster info for the current context.");
            if (infoResult.stderr) {
                console.log(color(infoResult.stderr.trim(), COLORS.dim));
            }
        }

        const correct = await askConfirm("Is this the correct cluster context?", true);
        if (correct) {
            return current;
        }

        const contexts = getContexts();
        if (contexts.length === 0) {
            throw new Error("No contexts found in kubeconfig.");
        }

        const selected = await askSelect(
            "Select a kube context",
            [
                ...contexts.map((contextName) => ({
                    label: contextName,
                    value: contextName,
                    description: "Use this context",
                })),
                {
                    label: "Exit",
                    value: "__exit__",
                    description: "Abort this run",
                },
            ],
            0,
        );

        if (selected === "__exit__") {
            throw new Error("Cancelled by user.");
        }

        runCommand("kubectl", ["config", "use-context", selected], { stdio: "inherit" });
    }
}

function valuesPaths(network: string): ValuesPaths {
    const networkDir = path.join(NETWORKS_ROOT, network);
    return {
        config: path.join(networkDir, "config.json"),
        infra: path.join(networkDir, "values.infra.yaml"),
        platform: path.join(networkDir, "values.platform.yaml"),
        dashboard: path.join(networkDir, "values.dashboard.yaml"),
    };
}

async function loadState(): Promise<StateFile> {
    return readJsonFile(STATE_FILE, {
        lastNetwork: "",
        lastContext: "",
        networks: {},
    });
}

async function saveState(state: StateFile): Promise<void> {
    await saveJsonFile(STATE_FILE, state);
}

function randomSecret() {
    return nodeCrypto.randomBytes(20).toString("hex");
}

function resolveStoredImageTag(existingTag: string | undefined): string {
    const normalizedTag = existingTag?.trim() ?? "";
    return normalizedTag || DEFAULT_IMAGE_VERSION;
}

function resolveDeployBackingServices(existing: ExistingNetworkConfig | null): boolean {
    if (typeof existing?.deployBackingServices === "boolean") {
        return existing.deployBackingServices;
    }
    return existing?.backingMode !== "external";
}

function resolveDeployDashboard(existing: ExistingNetworkConfig | null): boolean {
    if (typeof existing?.deployDashboard === "boolean") {
        return existing.deployDashboard;
    }
    return true;
}

function defaultConnections(namespace: string): NetworkConnections {
    return {
        mongodbUri: `mongodb://mongodb.${namespace}.svc.cluster.local:27017/ogcloud`,
        redisHost: `redis.${namespace}.svc.cluster.local`,
        redisPort: "6379",
        kafkaBrokers: `kafka.${namespace}.svc.cluster.local:9092`,
        minioEndpoint: `http://minio.${namespace}.svc.cluster.local:9000`,
        apiUrl: `http://api.${namespace}.svc.cluster.local:8080`,
    };
}

function defaultPodMinioEndpoint(namespace: string): string {
    return `minio.${namespace}.svc.cluster.local:9000`;
}

function resolvePodMinioEndpoint(minioEndpoint: string): string {
    const trimmed = minioEndpoint.trim();
    if (!trimmed.includes("://")) {
        return trimmed.replace(/\/+$/, "");
    }

    try {
        return new URL(trimmed).host;
    } catch {
        return trimmed.replace(/^[a-z]+:\/\//i, "").replace(/\/.*$/, "");
    }
}

async function generateConfig(
    networkArg: string | null | undefined,
    state: StateFile,
): Promise<void> {
    const fallbackNetwork = sanitizeNetworkName(networkArg || state.lastNetwork || "ogwars");
    let network = sanitizeNetworkName(networkArg);
    if (!network) {
        network = sanitizeNetworkName(
            await askInput("Network name", {
                defaultValue: fallbackNetwork,
                validator: (value) =>
                    validateNetworkName(value) || "Use lowercase letters, numbers and dashes only.",
            }),
        );
    }
    if (!validateNetworkName(network)) {
        throw new Error(
            `Invalid network name "${network}". Use lowercase letters, numbers and dashes.`,
        );
    }

    const paths = valuesPaths(network);
    const existing = await readJsonFile<ExistingNetworkConfig | null>(paths.config, null);

    const namespace = await askInput("Kubernetes namespace", {
        defaultValue: existing?.namespace || "ogcloud",
        validator: (value) =>
            validateNetworkName(value) || "Use lowercase letters, numbers and dashes only.",
    });

    const deployBackingServices = await askConfirm(
        "Deploy backing services?",
        resolveDeployBackingServices(existing),
    );
    const deployDashboard = await askConfirm(
        "Deploy frontend dashboard?",
        resolveDeployDashboard(existing),
    );
    const backingMode: BackingMode = deployBackingServices ? "managed" : "external";

    const ingressEnabled = await askConfirm(
        deployDashboard ? "Enable ingress for API and dashboard?" : "Enable ingress for API?",
        existing?.ingressEnabled || false,
    );
    const ingressClassName = ingressEnabled ? DEFAULT_INGRESS_CLASS_NAME : "";

    const apiDomain = ingressEnabled
        ? await askInput("API public domain", {
              defaultValue: existing?.apiDomain || `api.${network}.local`,
          })
        : "";

    const dashboardDomain =
        ingressEnabled && deployDashboard
            ? await askInput("Dashboard public domain", {
                  defaultValue: existing?.dashboardDomain || `dashboard.${network}.local`,
              })
            : "";

    const lbDomain = await askInput("Load balancer domain", {
        defaultValue: existing?.loadbalancerDomain || `mc.${network}.local`,
    });

    const apiImageTag = resolveStoredImageTag(existing?.imageTags?.api);
    const controllerImageTag = resolveStoredImageTag(existing?.imageTags?.controller);
    const loadbalancerImageTag = resolveStoredImageTag(existing?.imageTags?.loadbalancer);
    const dashboardImageTag = resolveStoredImageTag(existing?.imageTags?.dashboard);

    const apiEmail = await askInput("Service API email", {
        defaultValue: existing?.apiEmail || "service@ogcloud.local",
    });
    const apiPassword = await askInput("Service API password", {
        defaultValue: existing?.apiPassword || randomSecret(),
    });
    const podForwardingSecret = await askInput("Proxy forwarding secret", {
        defaultValue: existing?.podForwardingSecret || randomSecret(),
    });
    const jwtSecret = await askInput("JWT secret", {
        defaultValue: existing?.jwtSecret || randomSecret(),
    });
    const corsAllowedOrigin = deployDashboard
        ? await askInput("Dashboard CORS origin", {
              defaultValue: existing?.corsAllowedOrigin || "http://localhost:5173",
          })
        : existing?.corsAllowedOrigin || "";
    const minioAccessKey = await askInput("MinIO access key", {
        defaultValue: existing?.minioAccessKey || "minioadmin",
    });
    const minioSecretKey = await askInput("MinIO secret key", {
        defaultValue: existing?.minioSecretKey || "minioadmin123",
    });

    const connections =
        backingMode === "external"
            ? {
                  mongodbUri: await askInput("MongoDB URI", {
                      defaultValue:
                          existing?.connections?.mongodbUri ||
                          `mongodb://mongodb.${namespace}.svc.cluster.local:27017/ogcloud`,
                  }),
                  redisHost: await askInput("Redis host", {
                      defaultValue:
                          existing?.connections?.redisHost ||
                          `redis.${namespace}.svc.cluster.local`,
                  }),
                  redisPort: await askInput("Redis port", {
                      defaultValue: existing?.connections?.redisPort || "6379",
                  }),
                  kafkaBrokers: await askInput("Kafka brokers", {
                      defaultValue:
                          existing?.connections?.kafkaBrokers ||
                          `kafka.${namespace}.svc.cluster.local:9092`,
                  }),
                  minioEndpoint: await askInput("MinIO endpoint", {
                      defaultValue:
                          existing?.connections?.minioEndpoint ||
                          `http://minio.${namespace}.svc.cluster.local:9000`,
                  }),
                  apiUrl: `http://api.${namespace}.svc.cluster.local:8080`,
              }
            : defaultConnections(namespace);

    const defaultApiBaseUrl =
        existing?.apiBaseUrl ||
        (ingressEnabled
            ? `https://${apiDomain}`
            : `http://api.${namespace}.svc.cluster.local:8080`);
    const apiBaseUrl = deployDashboard
        ? await askInput("Dashboard runtime API base URL", {
              defaultValue: defaultApiBaseUrl,
          })
        : defaultApiBaseUrl;

    const infraValues: JsonMap =
        backingMode === "managed"
            ? {
                  namespace: { name: namespace },
                  minio: {
                      rootCredentials: {
                          accessKey: minioAccessKey,
                          secretKey: minioSecretKey,
                      },
                  },
              }
            : {
                  namespace: { name: namespace },
                  mongodb: { enabled: false },
                  redis: { enabled: false },
                  minio: { enabled: false },
                  kafka: { enabled: false },
              };

    const platformValues: JsonMap = {
        namespace: { name: namespace },
        connections,
        api: {
            image: {
                repository: FIXED_IMAGE_REPOSITORIES.api,
                tag: apiImageTag,
            },
            ingress: {
                enabled: ingressEnabled,
                className: ingressEnabled ? ingressClassName : "",
                domain: ingressEnabled ? apiDomain : `api.${network}.local`,
            },
            env: {
                minioAccessKey,
                minioSecretKey,
                minioBucket: "ogcloud-templates",
                jwtSecret,
                corsAllowedOrigin,
            },
        },
        controller: {
            image: {
                repository: FIXED_IMAGE_REPOSITORIES.controller,
                tag: controllerImageTag,
            },
            env: {
                apiEmail,
                apiPassword,
                minioAccessKey,
                minioSecretKey,
                podForwardingSecret,
                podMinioEndpoint:
                    backingMode === "external"
                        ? resolvePodMinioEndpoint(connections.minioEndpoint)
                        : defaultPodMinioEndpoint(namespace),
                podMinioAccessKey: minioAccessKey,
                podMinioSecretKey: minioSecretKey,
                podKafkaBrokers: connections.kafkaBrokers,
                podRedisHost: connections.redisHost,
                podRedisPort: connections.redisPort,
                podMongodbUri: connections.mongodbUri,
                podApiUrl: connections.apiUrl,
                podApiEmail: apiEmail,
                podApiPassword: apiPassword,
            },
        },
        loadbalancer: {
            image: {
                repository: FIXED_IMAGE_REPOSITORIES.loadbalancer,
                tag: loadbalancerImageTag,
            },
            service: {
                domain: lbDomain,
            },
            env: {
                apiEmail,
                apiPassword,
            },
        },
    };

    const dashboardValues: JsonMap = {
        namespace: { name: namespace },
        dashboard: {
            image: {
                repository: FIXED_IMAGE_REPOSITORIES.dashboard,
                tag: dashboardImageTag,
            },
            ingress: {
                enabled: deployDashboard && ingressEnabled,
                className: deployDashboard && ingressEnabled ? ingressClassName : "",
                domain:
                    deployDashboard && ingressEnabled
                        ? dashboardDomain
                        : existing?.dashboardDomain || `dashboard.${network}.local`,
            },
            runtime: {
                apiBaseUrl,
            },
        },
    };

    await fs.mkdir(path.dirname(paths.config), { recursive: true });
    await fs.writeFile(paths.infra, toYaml(infraValues), "utf8");
    await fs.writeFile(paths.platform, toYaml(platformValues), "utf8");
    await fs.writeFile(paths.dashboard, toYaml(dashboardValues), "utf8");

    const networkConfig: NetworkConfig = {
        schemaVersion: 2,
        network,
        namespace,
        deployBackingServices,
        deployDashboard,
        backingMode,
        ingressEnabled,
        ingressClassName,
        apiDomain,
        dashboardDomain,
        loadbalancerDomain: lbDomain,
        imageTags: {
            api: apiImageTag,
            controller: controllerImageTag,
            loadbalancer: loadbalancerImageTag,
            dashboard: dashboardImageTag,
        },
        apiEmail,
        apiPassword,
        podForwardingSecret,
        jwtSecret,
        corsAllowedOrigin,
        minioAccessKey,
        minioSecretKey,
        connections,
        apiBaseUrl,
        values: {
            infra: infraValues,
            platform: platformValues,
            dashboard: dashboardValues,
        },
        files: {
            infra: paths.infra,
            platform: paths.platform,
            dashboard: paths.dashboard,
        },
        updatedAt: new Date().toISOString(),
    };

    await saveJsonFile(paths.config, networkConfig);

    state.lastNetwork = network;
    state.networks[network] = {
        namespace,
        configPath: paths.config,
        updatedAt: networkConfig.updatedAt,
    };
    await saveState(state);

    ok(`Config generated for network "${network}"`);
    console.log(color(`  ${paths.infra}`, COLORS.dim));
    console.log(color(`  ${paths.platform}`, COLORS.dim));
    console.log(color(`  ${paths.dashboard}`, COLORS.dim));
}

function ensureNetworkConfigLoaded(network: string): Promise<NetworkConfig | null> {
    if (!network) {
        throw new Error("Network name is required.");
    }
    return readJsonFile<NetworkConfig | null>(valuesPaths(network).config, null);
}

function ensureNamespaceDoesNotExist(namespace: string): void {
    const result = runCommand("kubectl", ["get", "namespace", namespace], {
        allowFailure: true,
    });
    if (result.status === 0) {
        throw new Error(
            `Namespace "${namespace}" already exists. Clean deploy is blocked by design.`,
        );
    }
}

function helmUpgradeInstall(
    releaseName: string,
    chartPath: string,
    namespace: string,
    valuesFilePath: string,
    createNamespace: boolean,
): void {
    const args = [
        "upgrade",
        "--install",
        releaseName,
        chartPath,
        "--namespace",
        namespace,
        "-f",
        valuesFilePath,
        "--wait",
        "--timeout",
        "15m",
    ];

    if (createNamespace) {
        args.push("--create-namespace");
    }

    info(`helm ${args.join(" ")}`);
    runCommand("helm", args, { stdio: "inherit" });
}

async function waitForBackingServices(namespace: string, strict: boolean): Promise<void> {
    info(`Waiting for backing services in namespace "${namespace}"...`);
    const timeoutMs = 10 * 60 * 1000;
    const startedAt = Date.now();

    while (Date.now() - startedAt < timeoutMs) {
        const pending: string[] = [];
        for (const service of BACKING_SERVICES) {
            const result = runCommand(
                "kubectl",
                [
                    "get",
                    "endpoints",
                    service,
                    "-n",
                    namespace,
                    "-o",
                    "jsonpath={.subsets[0].addresses[0].ip}",
                ],
                { allowFailure: true },
            );

            if (result.status !== 0 || !(result.stdout || "").trim()) {
                pending.push(service);
            }
        }

        if (pending.length === 0) {
            ok("All backing services are reachable.");
            return;
        }

        process.stdout.write(
            `${color("Waiting", COLORS.yellow)} backing services: ${pending.join(", ")}   \r`,
        );
        await sleep(5000);
    }

    process.stdout.write("\n");
    const message = `Timed out waiting for backing services (${BACKING_SERVICES.join(", ")}).`;
    if (strict) {
        throw new Error(message);
    }
    warn(message);
}

function waitForDeployments(namespace: string, names: string[]): void {
    for (const deployment of names) {
        const exists = runCommand("kubectl", ["get", "deployment", deployment, "-n", namespace], {
            allowFailure: true,
        });
        if (exists.status !== 0) {
            warn(`Deployment "${deployment}" not found. Skipping rollout wait.`);
            continue;
        }
        info(`Waiting for deployment/${deployment} rollout...`);
        runCommand(
            "kubectl",
            ["rollout", "status", `deployment/${deployment}`, "-n", namespace, "--timeout=10m"],
            { stdio: "inherit" },
        );
    }
}

async function deploy(network: string, helmRoot: string, state: StateFile): Promise<void> {
    const config = await ensureNetworkConfigLoaded(network);
    if (!config) {
        throw new Error(
            `No config found for network "${network}". Run --generate-config ${network} first.`,
        );
    }

    const namespace = config.namespace;
    const deployBackingServices = resolveDeployBackingServices(config);
    const deployDashboard = resolveDeployDashboard(config);
    ensureNamespaceDoesNotExist(namespace);

    const paths = valuesPaths(network);
    info(`Starting clean deploy for network "${network}" in namespace "${namespace}"`);

    if (deployBackingServices) {
        helmUpgradeInstall(
            RELEASES.infra,
            path.join(helmRoot, "ogcloud-infra"),
            namespace,
            paths.infra,
            true,
        );

        if (config.backingMode === "managed") {
            // Helm --wait confirms release resources become ready. This adds endpoint-level
            // validation before platform charts install, so managed backing can take longer overall.
            await waitForBackingServices(namespace, true);
        }
    } else {
        info("Skipping infra deployment per generated config.");
        info("External backing mode is configured. Skipping in-cluster backing health wait.");
    }

    helmUpgradeInstall(
        RELEASES.platform,
        path.join(helmRoot, "ogcloud-platform"),
        namespace,
        paths.platform,
        true,
    );

    if (deployDashboard) {
        helmUpgradeInstall(
            RELEASES.dashboard,
            path.join(helmRoot, "ogcloud-dashboard"),
            namespace,
            paths.dashboard,
            false,
        );
    } else {
        info("Skipping dashboard deployment per generated config.");
    }

    const deploymentsToWait = ["api", "controller", "loadbalancer"];
    if (deployDashboard) {
        deploymentsToWait.push("dashboard");
    }
    waitForDeployments(namespace, deploymentsToWait);

    state.lastNetwork = network;
    state.networks[network] = {
        namespace,
        configPath: paths.config,
        updatedAt: new Date().toISOString(),
    };
    await saveState(state);

    ok(`Deploy finished for network "${network}".`);
}

async function update(
    network: string,
    componentArg: string,
    imageVersion: string,
    helmRoot: string,
    state: StateFile,
): Promise<void> {
    const component = sanitizeComponent(componentArg);
    if (!isComponentName(component)) {
        throw new Error(`Invalid component "${componentArg}". Allowed: ${COMPONENTS.join(", ")}`);
    }
    if (!imageVersion) {
        throw new Error("Image version is required for update.");
    }

    const config = await ensureNetworkConfigLoaded(network);
    if (!config) {
        throw new Error(
            `No config found for network "${network}". Run --generate-config ${network} first.`,
        );
    }

    const mapping = {
        dashboard: {
            valuesRoot: "dashboard",
            tagPath: ["dashboard", "image", "tag"],
            imageTagKey: "dashboard",
            chart: "ogcloud-dashboard",
            release: RELEASES.dashboard,
            waitFor: ["dashboard"],
        },
        api: {
            valuesRoot: "platform",
            tagPath: ["api", "image", "tag"],
            imageTagKey: "api",
            chart: "ogcloud-platform",
            release: RELEASES.platform,
            waitFor: ["api"],
        },
        loadbalancer: {
            valuesRoot: "platform",
            tagPath: ["loadbalancer", "image", "tag"],
            imageTagKey: "loadbalancer",
            chart: "ogcloud-platform",
            release: RELEASES.platform,
            waitFor: ["loadbalancer"],
        },
        controller: {
            valuesRoot: "platform",
            tagPath: ["controller", "image", "tag"],
            imageTagKey: "controller",
            chart: "ogcloud-platform",
            release: RELEASES.platform,
            waitFor: ["controller"],
        },
    } satisfies Record<
        ComponentName,
        {
            valuesRoot: ValuesFileKey;
            tagPath: string[];
            imageTagKey: keyof ImageTags;
            chart: "ogcloud-dashboard" | "ogcloud-platform";
            release: string;
            waitFor: string[];
        }
    >;

    const target = mapping[component];
    setDeepValue(config.values[target.valuesRoot], target.tagPath, imageVersion);

    const paths = valuesPaths(network);
    await fs.writeFile(paths[target.valuesRoot], toYaml(config.values[target.valuesRoot]), "utf8");
    config.imageTags[target.imageTagKey] = imageVersion;
    config.updatedAt = new Date().toISOString();
    await saveJsonFile(paths.config, config);

    info(`Updating ${component} image tag to ${imageVersion} and reinstalling chart.`);
    helmUpgradeInstall(
        target.release,
        path.join(helmRoot, target.chart),
        config.namespace,
        paths[target.valuesRoot],
        false,
    );
    waitForDeployments(config.namespace, target.waitFor);

    state.lastNetwork = network;
    state.networks[network] = {
        namespace: config.namespace,
        configPath: paths.config,
        updatedAt: config.updatedAt,
    };
    await saveState(state);
    ok(`Component "${component}" updated to image tag "${imageVersion}".`);
}

async function destroy(network: string, state: StateFile): Promise<void> {
    const config = await ensureNetworkConfigLoaded(network);
    const namespace =
        config?.namespace ||
        (await askInput("Namespace to destroy", {
            defaultValue: "ogcloud",
        }));

    const confirmed = await askConfirm(
        `Destroy network "${network}" in namespace "${namespace}"? This will uninstall charts and delete the namespace.`,
        false,
    );
    if (!confirmed) {
        warn("Destroy cancelled.");
        return;
    }

    const releases = [RELEASES.dashboard, RELEASES.platform, RELEASES.infra];
    for (const release of releases) {
        info(`Uninstalling ${release}...`);
        runCommand("helm", ["uninstall", release, "-n", namespace], {
            allowFailure: true,
            stdio: "inherit",
        });
    }

    info(`Deleting namespace ${namespace}...`);
    runCommand(
        "kubectl",
        ["delete", "namespace", namespace, "--ignore-not-found=true", "--wait=true"],
        {
            allowFailure: true,
            stdio: "inherit",
        },
    );

    await fs.rm(path.dirname(valuesPaths(network).config), { recursive: true, force: true });
    delete state.networks[network];
    if (state.lastNetwork === network) {
        state.lastNetwork = "";
    }
    await saveState(state);

    ok(`Network "${network}" destroyed.`);
}

async function chooseCommandInteractively(
    parsed: ParsedArgs,
    state: StateFile,
): Promise<ParsedArgs> {
    if (!parsed.interactive && parsed.command) {
        return parsed;
    }

    const command = await askSelect<CliCommand | "exit">(
        "Select an action",
        [
            {
                label: "Generate config",
                value: "generate-config",
                description: "Create or update values YAML files",
            },
            { label: "Deploy", value: "deploy", description: "Clean install of OgCloud charts" },
            { label: "Update", value: "update", description: "Update one component image tag" },
            { label: "Destroy", value: "destroy", description: "Uninstall and remove namespace" },
            { label: "Exit", value: "exit", description: "Abort" },
        ],
        0,
    );

    if (command === "exit") {
        throw new Error("Cancelled by user.");
    }
    const selectedCommand = command as CliCommand;

    const network = sanitizeNetworkName(
        await askInput("Network name", {
            defaultValue: sanitizeNetworkName(parsed.network || state.lastNetwork || "ogwars"),
            validator: (value) =>
                validateNetworkName(value) || "Use lowercase letters, numbers and dashes only.",
        }),
    );

    if (selectedCommand === "deploy") {
        return { ...parsed, command: selectedCommand, network };
    }

    if (selectedCommand === "update") {
        const component = await askSelect(
            "Select component",
            COMPONENTS.map((name) => ({
                label: name,
                value: name,
                description: "Update this component image tag",
            })),
            1,
        );
        const imageVersion = await askInput("Image version (e.g. 0.0.1)", {
            defaultValue: "0.0.1",
        });
        return { ...parsed, command: selectedCommand, network, component, imageVersion };
    }

    return { ...parsed, command: selectedCommand, network };
}

async function completeMissingArgs(parsed: ParsedArgs, state: StateFile): Promise<ParsedArgs> {
    const completed = { ...parsed };

    if (!completed.command) {
        return completed;
    }

    if (!completed.network) {
        completed.network = sanitizeNetworkName(
            await askInput("Network name", {
                defaultValue: sanitizeNetworkName(state.lastNetwork || "ogwars"),
                validator: (value) =>
                    validateNetworkName(value) || "Use lowercase letters, numbers and dashes only.",
            }),
        );
    } else {
        completed.network = sanitizeNetworkName(completed.network);
    }

    if (!validateNetworkName(completed.network)) {
        throw new Error(
            `Invalid network name "${completed.network}". Use lowercase letters, numbers and dashes.`,
        );
    }

    if (completed.command === "update") {
        if (!completed.component) {
            completed.component = await askSelect(
                "Select component",
                COMPONENTS.map((name) => ({ label: name, value: name })),
            );
        }
        completed.component = sanitizeComponent(completed.component);
        if (!isComponentName(completed.component)) {
            throw new Error(
                `Invalid component "${completed.component}". Allowed: ${COMPONENTS.join(", ")}`,
            );
        }

        if (!completed.imageVersion) {
            completed.imageVersion = await askInput("Image version (e.g. 0.0.1)", {
                defaultValue: "0.0.1",
            });
        }
    }

    return completed;
}

async function main(): Promise<void> {
    const parsed = parseArgs(process.argv.slice(2));
    if (parsed.help) {
        printHelp();
        return;
    }

    heading("\nOgCloud Quickstart Setup\n");
    info("Checking dependencies...");
    ensureDependencies();
    ok("Dependencies available: node, kubectl, helm, npm, npx");

    const state = await loadState();

    let commandContext = await chooseCommandInteractively(parsed, state);
    commandContext = await completeMissingArgs(commandContext, state);

    state.lastContext = await ensureClusterContext(commandContext.context || state.lastContext);
    await saveState(state);

    if (!commandContext.command) {
        printHelp();
        return;
    }

    const needsHelmDownload =
        commandContext.command === "generate-config" ||
        commandContext.command === "deploy" ||
        commandContext.command === "update";

    let helmRoot: string | null = null;
    if (needsHelmDownload) {
        helmRoot = await ensureHelmCache(commandContext.refreshHelm);
    }

    switch (commandContext.command) {
        case "generate-config":
            await generateConfig(commandContext.network, state);
            return;
        case "deploy": {
            if (!commandContext.network) {
                throw new Error("Network name is required.");
            }
            if (!helmRoot) {
                throw new Error("Helm cache path is unavailable. Cannot deploy.");
            }
            if (commandContext.withoutBacking) {
                warn(
                    "--without-backing is deprecated and ignored. Use --generate-config to store deploy choices.",
                );
            }
            await deploy(commandContext.network, helmRoot, state);
            return;
        }
        case "update": {
            if (!commandContext.network) {
                throw new Error("Network name is required.");
            }
            if (!helmRoot) {
                throw new Error("Helm cache path is unavailable. Cannot update.");
            }
            if (!commandContext.component) {
                throw new Error("Component is required for update.");
            }
            if (!commandContext.imageVersion) {
                throw new Error("Image version is required for update.");
            }
            await update(
                commandContext.network,
                commandContext.component,
                commandContext.imageVersion,
                helmRoot,
                state,
            );
            return;
        }
        case "destroy":
            if (!commandContext.network) {
                throw new Error("Network name is required.");
            }
            await destroy(commandContext.network, state);
            return;
        default:
            throw new Error(`Unsupported command: ${commandContext.command}`);
    }
}

main()
    .catch((error) => {
        fail(error.message || String(error));
        process.exitCode = 1;
    })
    .finally(() => {
        closePromptInterface();
    });
