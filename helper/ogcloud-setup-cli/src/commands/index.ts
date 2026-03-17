import * as fs from "node:fs/promises";
import * as path from "node:path";

import { COMPONENTS, RELEASES } from "../shared/constants";
import { info, ok, warn } from "../cli/output";
import { helmUpgradeInstall, helmUninstall } from "../infra/helm";
import {
    deleteNamespace,
    ensureNamespaceDoesNotExist,
    waitForBackingServices,
    waitForDeployments,
} from "../infra/kubernetes";
import {
    generateConfig,
    resolveDeployBackingServices,
    resolveDeployDashboard,
} from "./network-config";
import { askConfirm, askInput } from "../cli/prompt";
import { saveJsonFile, setDeepValue, toYaml } from "../shared/serialization";
import {
    forgetNetwork,
    loadNetworkConfig,
    rememberNetwork,
    saveState,
    valuesPaths,
} from "../shared/state";
import { isComponentName, sanitizeComponent } from "../shared/validation";
import type {
    CommandDefinition,
    CommandExecutionContext,
    ComponentName,
    ImageTags,
    JsonMap,
    ValuesFileKey,
} from "../shared/types";

type UpdateTarget = {
    valuesRoot: ValuesFileKey;
    valuePaths: string[][];
    imageTagKey: keyof ImageTags;
    chart: "ogcloud-dashboard" | "ogcloud-platform";
    release: string;
    waitFor: string[];
};

const UPDATE_TARGETS = {
    dashboard: {
        valuesRoot: "dashboard",
        valuePaths: [["dashboard", "image", "tag"]],
        imageTagKey: "dashboard",
        chart: "ogcloud-dashboard",
        release: RELEASES.dashboard,
        waitFor: ["dashboard"],
    },
    api: {
        valuesRoot: "platform",
        valuePaths: [["api", "image", "tag"]],
        imageTagKey: "api",
        chart: "ogcloud-platform",
        release: RELEASES.platform,
        waitFor: ["api"],
    },
    loadbalancer: {
        valuesRoot: "platform",
        valuePaths: [["loadbalancer", "image", "tag"]],
        imageTagKey: "loadbalancer",
        chart: "ogcloud-platform",
        release: RELEASES.platform,
        waitFor: ["loadbalancer"],
    },
    controller: {
        valuesRoot: "platform",
        valuePaths: [["controller", "image", "tag"]],
        imageTagKey: "controller",
        chart: "ogcloud-platform",
        release: RELEASES.platform,
        waitFor: ["controller"],
    },
    "template-loader": {
        valuesRoot: "platform",
        valuePaths: [["controller", "env", "templateLoaderVersion"]],
        imageTagKey: "templateLoader",
        chart: "ogcloud-platform",
        release: RELEASES.platform,
        waitFor: ["controller"],
    },
} satisfies Record<ComponentName, UpdateTarget>;

function requireNetworkName(network: string | null): string {
    if (!network) {
        throw new Error("Network name is required.");
    }
    return network;
}

function requireHelmRoot(helmRoot: string | null, action: string): string {
    if (!helmRoot) {
        throw new Error(`Helm cache path is unavailable. Cannot ${action}.`);
    }
    return helmRoot;
}

async function executeDeploy({ parsed, state, helmRoot }: CommandExecutionContext): Promise<void> {
    const network = requireNetworkName(parsed.network);
    const resolvedHelmRoot = requireHelmRoot(helmRoot, "deploy");
    if (parsed.withoutBacking) {
        warn(
            "--without-backing is deprecated and ignored. Use --generate-config to store deploy choices.",
        );
    }

    const config = await loadNetworkConfig(network);
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
            path.join(resolvedHelmRoot, "ogcloud-infra"),
            namespace,
            paths.infra,
            true,
        );

        if (config.backingMode === "managed") {
            await waitForBackingServices(namespace, true);
        }
    } else {
        info("Skipping infra deployment per generated config.");
        info("External backing mode is configured. Skipping in-cluster backing health wait.");
    }

    helmUpgradeInstall(
        RELEASES.platform,
        path.join(resolvedHelmRoot, "ogcloud-platform"),
        namespace,
        paths.platform,
        true,
    );

    if (deployDashboard) {
        helmUpgradeInstall(
            RELEASES.dashboard,
            path.join(resolvedHelmRoot, "ogcloud-dashboard"),
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

    rememberNetwork(state, network, namespace, paths.config, new Date().toISOString());
    await saveState(state);
    ok(`Deploy finished for network "${network}".`);
}

async function executeUpdate({ parsed, state, helmRoot }: CommandExecutionContext): Promise<void> {
    const network = requireNetworkName(parsed.network);
    const resolvedHelmRoot = requireHelmRoot(helmRoot, "update");
    const component = sanitizeComponent(parsed.component);
    const imageVersion = parsed.imageVersion;

    if (!isComponentName(component)) {
        throw new Error(
            `Invalid component "${parsed.component}". Allowed: ${COMPONENTS.join(", ")}`,
        );
    }
    if (!imageVersion) {
        throw new Error("Image version is required for update.");
    }

    const config = await loadNetworkConfig(network);
    if (!config) {
        throw new Error(
            `No config found for network "${network}". Run --generate-config ${network} first.`,
        );
    }

    const target = UPDATE_TARGETS[component];
    for (const valuePath of target.valuePaths) {
        setDeepValue(config.values[target.valuesRoot] as JsonMap, valuePath, imageVersion);
    }

    const paths = valuesPaths(network);
    await fs.writeFile(paths[target.valuesRoot], toYaml(config.values[target.valuesRoot]), "utf8");
    config.imageTags[target.imageTagKey] = imageVersion;
    config.updatedAt = new Date().toISOString();
    await saveJsonFile(paths.config, config);

    info(`Updating ${component} image tag to ${imageVersion} and reinstalling chart.`);
    helmUpgradeInstall(
        target.release,
        path.join(resolvedHelmRoot, target.chart),
        config.namespace,
        paths[target.valuesRoot],
        false,
    );
    waitForDeployments(config.namespace, target.waitFor);

    rememberNetwork(state, network, config.namespace, paths.config, config.updatedAt);
    await saveState(state);
    ok(`Component "${component}" updated to image tag "${imageVersion}".`);
}

async function executeDestroy({ parsed, state }: CommandExecutionContext): Promise<void> {
    const network = requireNetworkName(parsed.network);
    const config = await loadNetworkConfig(network);
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

    helmUninstall(RELEASES.dashboard, namespace);
    helmUninstall(RELEASES.platform, namespace);
    helmUninstall(RELEASES.infra, namespace);
    deleteNamespace(namespace);

    await fs.rm(path.dirname(valuesPaths(network).config), { recursive: true, force: true });
    forgetNetwork(state, network);
    await saveState(state);
    ok(`Network "${network}" destroyed.`);
}

export const COMMAND_DEFINITIONS: Record<CommandDefinition["name"], CommandDefinition> = {
    "generate-config": {
        name: "generate-config",
        description: "Create or update values YAML files",
        requiresClusterContext: false,
        requiresHelmCache: false,
        requiredCommands: [],
        execute: async ({ parsed, state }) => {
            await generateConfig(parsed.network, state);
        },
    },
    deploy: {
        name: "deploy",
        description: "Clean install of OgCloud charts",
        requiresClusterContext: true,
        requiresHelmCache: true,
        requiredCommands: ["kubectl", "helm"],
        execute: executeDeploy,
    },
    update: {
        name: "update",
        description: "Update one component image tag",
        requiresClusterContext: true,
        requiresHelmCache: true,
        requiredCommands: ["kubectl", "helm"],
        execute: executeUpdate,
    },
    destroy: {
        name: "destroy",
        description: "Uninstall and remove namespace",
        requiresClusterContext: true,
        requiresHelmCache: false,
        requiredCommands: ["kubectl", "helm"],
        execute: executeDestroy,
    },
};
