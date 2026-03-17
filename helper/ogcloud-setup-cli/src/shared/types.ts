export type CliCommand = "generate-config" | "deploy" | "update" | "destroy";
export type BackingMode = "managed" | "external";
export type ComponentName =
    | "dashboard"
    | "api"
    | "loadbalancer"
    | "controller"
    | "template-loader";
export type ValuesFileKey = "infra" | "platform" | "dashboard";
export type JsonMap = Record<string, unknown>;

export type Choice<T = string> = {
    label: string;
    value: T;
    description?: string;
};

export type ParsedArgs = {
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

export type RunCommandOptions = {
    stdio?: "pipe" | "inherit";
    cwd?: string;
    allowFailure?: boolean;
};

export type AskInputOptions = {
    defaultValue?: unknown;
    required?: boolean;
    validator?: (value: string) => true | string;
};

export type StateNetworkRecord = {
    namespace: string;
    configPath: string;
    updatedAt: string;
};

export type StateFile = {
    lastNetwork: string;
    lastContext: string;
    networks: Record<string, StateNetworkRecord>;
};

export type NetworkConnections = {
    mongodbUri: string;
    redisHost: string;
    redisPort: string;
    kafkaBrokers: string;
    minioEndpoint: string;
    apiUrl: string;
};

export type ImageTags = {
    api: string;
    controller: string;
    loadbalancer: string;
    dashboard: string;
    templateLoader: string;
};

export type NetworkValues = Record<ValuesFileKey, JsonMap>;
export type NetworkFiles = Record<ValuesFileKey, string>;

export type NetworkConfig = {
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

export type ExistingNetworkConfig = Partial<NetworkConfig> & {
    imageTags?: Partial<ImageTags>;
    connections?: Partial<NetworkConnections>;
    values?: Partial<NetworkValues>;
    files?: Partial<NetworkFiles>;
};

export type ValuesPaths = {
    config: string;
    infra: string;
    platform: string;
    dashboard: string;
};

export type RequiredCommand = "kubectl" | "helm" | "npm" | "npx" | "node";

export type CommandExecutionContext = {
    parsed: ParsedArgs;
    state: StateFile;
    helmRoot: string | null;
};

export type CommandDefinition = {
    name: CliCommand;
    description: string;
    requiresClusterContext: boolean;
    requiresHelmCache: boolean;
    requiredCommands: readonly RequiredCommand[];
    execute(context: CommandExecutionContext): Promise<void>;
};
