import * as fs from "node:fs/promises";
import * as nodeCrypto from "node:crypto";
import * as path from "node:path";

import {
    COLORS,
    DEFAULT_IMAGE_VERSION,
    DEFAULT_INGRESS_CLASS_NAME,
    FIXED_IMAGE_REPOSITORIES,
} from "../shared/constants";
import { color, ok } from "../cli/output";
import { askConfirm, askInput } from "../cli/prompt";
import { getDeepValue, readJsonFile, saveJsonFile, toYaml } from "../shared/serialization";
import { rememberNetwork, saveState, valuesPaths } from "../shared/state";
import { sanitizeNetworkName, validateNetworkName } from "../shared/validation";
import type {
    BackingMode,
    ExistingNetworkConfig,
    JsonMap,
    NetworkConfig,
    NetworkConnections,
    StateFile,
} from "../shared/types";

function randomSecret(): string {
    return nodeCrypto.randomBytes(20).toString("hex");
}

function resolveStoredImageTag(existingTag: string | undefined): string {
    const normalizedTag = existingTag?.trim() ?? "";
    return normalizedTag || DEFAULT_IMAGE_VERSION;
}

function resolveStoredTemplateLoaderVersion(
    existing: ExistingNetworkConfig | null,
    fallbackVersion: string,
): string {
    const storedImageTag = existing?.imageTags?.templateLoader;
    if (typeof storedImageTag === "string") {
        const normalizedImageTag = storedImageTag.trim();
        if (normalizedImageTag) {
            return normalizedImageTag;
        }
    }

    const storedValue = getDeepValue(existing?.values?.platform, [
        "controller",
        "env",
        "templateLoaderVersion",
    ]);
    if (typeof storedValue !== "string") {
        return fallbackVersion;
    }

    const normalizedVersion = storedValue.trim();
    return normalizedVersion || fallbackVersion;
}

export function resolveDeployBackingServices(existing: ExistingNetworkConfig | null): boolean {
    if (typeof existing?.deployBackingServices === "boolean") {
        return existing.deployBackingServices;
    }
    return existing?.backingMode !== "external";
}

export function resolveDeployDashboard(existing: ExistingNetworkConfig | null): boolean {
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

async function promptConnections(
    existing: ExistingNetworkConfig | null,
    namespace: string,
    backingMode: BackingMode,
): Promise<NetworkConnections> {
    if (backingMode === "managed") {
        return defaultConnections(namespace);
    }

    return {
        mongodbUri: await askInput("MongoDB URI", {
            defaultValue:
                existing?.connections?.mongodbUri ||
                `mongodb://mongodb.${namespace}.svc.cluster.local:27017/ogcloud`,
        }),
        redisHost: await askInput("Redis host", {
            defaultValue:
                existing?.connections?.redisHost || `redis.${namespace}.svc.cluster.local`,
        }),
        redisPort: await askInput("Redis port", {
            defaultValue: existing?.connections?.redisPort || "6379",
        }),
        kafkaBrokers: await askInput("Kafka brokers", {
            defaultValue:
                existing?.connections?.kafkaBrokers || `kafka.${namespace}.svc.cluster.local:9092`,
        }),
        minioEndpoint: await askInput("MinIO endpoint", {
            defaultValue:
                existing?.connections?.minioEndpoint ||
                `http://minio.${namespace}.svc.cluster.local:9000`,
        }),
        apiUrl: `http://api.${namespace}.svc.cluster.local:8080`,
    };
}

function buildInfraValues(
    backingMode: BackingMode,
    namespace: string,
    minioAccessKey: string,
    minioSecretKey: string,
): JsonMap {
    if (backingMode === "managed") {
        return {
            namespace: { name: namespace },
            minio: {
                rootCredentials: {
                    accessKey: minioAccessKey,
                    secretKey: minioSecretKey,
                },
            },
        };
    }

    return {
        namespace: { name: namespace },
        mongodb: { enabled: false },
        redis: { enabled: false },
        minio: { enabled: false },
        kafka: { enabled: false },
    };
}

type BuildPlatformValuesInput = {
    network: string;
    namespace: string;
    ingressEnabled: boolean;
    ingressClassName: string;
    apiDomain: string;
    lbDomain: string;
    backingMode: BackingMode;
    connections: NetworkConnections;
    apiImageTag: string;
    controllerImageTag: string;
    loadbalancerImageTag: string;
    apiEmail: string;
    apiPassword: string;
    minioAccessKey: string;
    minioSecretKey: string;
    podForwardingSecret: string;
    templateLoaderVersion: string;
    jwtSecret: string;
    corsAllowedOrigin: string;
};

function buildPlatformValues(input: BuildPlatformValuesInput): JsonMap {
    return {
        namespace: { name: input.namespace },
        connections: input.connections,
        api: {
            image: {
                repository: FIXED_IMAGE_REPOSITORIES.api,
                tag: input.apiImageTag,
            },
            ingress: {
                enabled: input.ingressEnabled,
                className: input.ingressEnabled ? input.ingressClassName : "",
                domain: input.ingressEnabled ? input.apiDomain : `api.${input.network}.local`,
            },
            env: {
                minioAccessKey: input.minioAccessKey,
                minioSecretKey: input.minioSecretKey,
                minioBucket: "ogcloud-templates",
                jwtSecret: input.jwtSecret,
                corsAllowedOrigin: input.corsAllowedOrigin,
            },
        },
        controller: {
            image: {
                repository: FIXED_IMAGE_REPOSITORIES.controller,
                tag: input.controllerImageTag,
            },
            env: {
                apiEmail: input.apiEmail,
                apiPassword: input.apiPassword,
                minioAccessKey: input.minioAccessKey,
                minioSecretKey: input.minioSecretKey,
                podForwardingSecret: input.podForwardingSecret,
                templateLoaderVersion: input.templateLoaderVersion,
                podMinioEndpoint:
                    input.backingMode === "external"
                        ? resolvePodMinioEndpoint(input.connections.minioEndpoint)
                        : defaultPodMinioEndpoint(input.namespace),
                podMinioAccessKey: input.minioAccessKey,
                podMinioSecretKey: input.minioSecretKey,
                podKafkaBrokers: input.connections.kafkaBrokers,
                podRedisHost: input.connections.redisHost,
                podRedisPort: input.connections.redisPort,
                podMongodbUri: input.connections.mongodbUri,
                podApiUrl: input.connections.apiUrl,
                podApiEmail: input.apiEmail,
                podApiPassword: input.apiPassword,
            },
        },
        loadbalancer: {
            image: {
                repository: FIXED_IMAGE_REPOSITORIES.loadbalancer,
                tag: input.loadbalancerImageTag,
            },
            service: {
                domain: input.lbDomain,
            },
            env: {
                apiEmail: input.apiEmail,
                apiPassword: input.apiPassword,
            },
        },
    };
}

function buildDashboardValues(
    namespace: string,
    dashboardImageTag: string,
    deployDashboard: boolean,
    ingressEnabled: boolean,
    ingressClassName: string,
    dashboardDomain: string,
    fallbackDashboardDomain: string,
    apiBaseUrl: string,
): JsonMap {
    return {
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
                    deployDashboard && ingressEnabled ? dashboardDomain : fallbackDashboardDomain,
            },
            runtime: {
                apiBaseUrl,
            },
        },
    };
}

export async function generateConfig(
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
    const templateLoaderVersion = resolveStoredTemplateLoaderVersion(existing, controllerImageTag);

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

    const connections = await promptConnections(existing, namespace, backingMode);

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

    const infraValues = buildInfraValues(backingMode, namespace, minioAccessKey, minioSecretKey);
    const platformValues = buildPlatformValues({
        network,
        namespace,
        ingressEnabled,
        ingressClassName,
        apiDomain,
        lbDomain,
        backingMode,
        connections,
        apiImageTag,
        controllerImageTag,
        loadbalancerImageTag,
        apiEmail,
        apiPassword,
        minioAccessKey,
        minioSecretKey,
        podForwardingSecret,
        templateLoaderVersion,
        jwtSecret,
        corsAllowedOrigin,
    });
    const dashboardValues = buildDashboardValues(
        namespace,
        dashboardImageTag,
        deployDashboard,
        ingressEnabled,
        ingressClassName,
        dashboardDomain,
        existing?.dashboardDomain || `dashboard.${network}.local`,
        apiBaseUrl,
    );

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
            templateLoader: templateLoaderVersion,
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
    rememberNetwork(state, network, namespace, paths.config, networkConfig.updatedAt);
    await saveState(state);

    ok(`Config generated for network "${network}"`);
    console.log(color(`  ${paths.infra}`, COLORS.dim));
    console.log(color(`  ${paths.platform}`, COLORS.dim));
    console.log(color(`  ${paths.dashboard}`, COLORS.dim));
}
