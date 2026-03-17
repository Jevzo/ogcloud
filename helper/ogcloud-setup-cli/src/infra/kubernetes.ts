import { BACKING_SERVICES, COLORS } from "../shared/constants";
import { color, info, ok, warn } from "../cli/output";
import { askConfirm, askSelect } from "../cli/prompt";
import { runCommand } from "./process";

function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
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

export async function ensureClusterContext(requestedContext?: string | null): Promise<string> {
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

export function ensureNamespaceDoesNotExist(namespace: string): void {
    const result = runCommand("kubectl", ["get", "namespace", namespace], {
        allowFailure: true,
    });
    if (result.status === 0) {
        throw new Error(
            `Namespace "${namespace}" already exists. Clean deploy is blocked by design.`,
        );
    }
}

export async function waitForBackingServices(namespace: string, strict: boolean): Promise<void> {
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

export function waitForDeployments(namespace: string, names: string[]): void {
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

export function deleteNamespace(namespace: string): void {
    info(`Deleting namespace ${namespace}...`);
    runCommand(
        "kubectl",
        ["delete", "namespace", namespace, "--ignore-not-found=true", "--wait=true"],
        {
            allowFailure: true,
            stdio: "inherit",
        },
    );
}
