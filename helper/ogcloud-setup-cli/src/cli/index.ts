import { COMMAND_ORDER, COMPONENTS } from "../shared/constants";
import { COMMAND_DEFINITIONS } from "../commands";
import { askInput, askSelect } from "./prompt";
import {
    isComponentName,
    sanitizeComponent,
    sanitizeNetworkName,
    validateNetworkName,
} from "../shared/validation";
import type { CliCommand, ParsedArgs, StateFile } from "../shared/types";

function commandLabel(command: CliCommand): string {
    switch (command) {
        case "generate-config":
            return "Generate config";
        case "deploy":
            return "Deploy";
        case "update":
            return "Update";
        case "destroy":
            return "Destroy";
    }
}

function componentDescription(component: string): string {
    if (component === "template-loader") {
        return "Update the template-loader image tag used by controller-managed pods";
    }
    return "Update this component image tag";
}

export function parseArgs(argv: string[]): ParsedArgs {
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

export function printHelp(): void {
    console.log(`
OgCloud Setup CLI
Quickstart lifecycle utility for OgCloud Helm deployment.

Usage
  ogcloud-setup --generate-config <network_name>
  ogcloud-setup --deploy <network_name>
  ogcloud-setup --update <network_name> <component> <image_version>
  ogcloud-setup --destroy <network_name>

Interactive mode
  ogcloud-setup
  npx @ogcloud/setup ogwars

npx examples
  npx @ogcloud/setup --generate-config ogwars
  npx @ogcloud/setup --deploy ogwars
  npx @ogcloud/setup

Flags
  --context <name>       Use a specific kube context before prompting
  --refresh-helm         Force fresh download of remote Helm files
  --without-backing      Deprecated; ignored. Backing deployment is now stored in config
  --interactive          Open guided flow
  --help                 Show this help
`);
}

export async function chooseCommandInteractively(
    parsed: ParsedArgs,
    state: StateFile,
): Promise<ParsedArgs> {
    if (!parsed.interactive && parsed.command) {
        return parsed;
    }

    const command = await askSelect<CliCommand | "exit">(
        "Select an action",
        [
            ...COMMAND_ORDER.map((entry) => ({
                label: commandLabel(entry),
                value: entry,
                description: COMMAND_DEFINITIONS[entry].description,
            })),
            { label: "Exit", value: "exit", description: "Abort" },
        ],
        0,
    );

    if (command === "exit") {
        throw new Error("Cancelled by user.");
    }

    const network = sanitizeNetworkName(
        await askInput("Network name", {
            defaultValue: sanitizeNetworkName(parsed.network || state.lastNetwork || "ogwars"),
            validator: (value) =>
                validateNetworkName(value) || "Use lowercase letters, numbers and dashes only.",
        }),
    );

    if (command === "deploy") {
        return { ...parsed, command, network };
    }

    if (command === "update") {
        const component = await askSelect(
            "Select component",
            COMPONENTS.map((name) => ({
                label: name,
                value: name,
                description: componentDescription(name),
            })),
            1,
        );
        const imageVersion = await askInput("Image version (e.g. 0.0.1)", {
            defaultValue: "0.0.1",
        });
        return { ...parsed, command, network, component, imageVersion };
    }

    return { ...parsed, command, network };
}

export async function completeMissingArgs(
    parsed: ParsedArgs,
    state: StateFile,
): Promise<ParsedArgs> {
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
