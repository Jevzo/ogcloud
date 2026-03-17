import { COMMAND_DEFINITIONS } from "../commands";
import { completeMissingArgs, chooseCommandInteractively, parseArgs, printHelp } from "../cli";
import { heading, info, ok } from "../cli/output";
import { ensureHelmCache } from "../infra/helm-cache";
import { ensureClusterContext } from "../infra/kubernetes";
import { closePromptInterface } from "../cli/prompt";
import { ensureCommandsAvailable } from "../infra/process";
import { loadState, saveState } from "../shared/state";

function dependencySummary(commandName: keyof typeof COMMAND_DEFINITIONS): string {
    const dependencies = [...new Set(COMMAND_DEFINITIONS[commandName].requiredCommands)];
    return dependencies.join(", ");
}

export async function runCli(argv: string[]): Promise<void> {
    const parsed = parseArgs(argv);
    if (parsed.help) {
        printHelp();
        return;
    }

    heading("\nOgCloud Quickstart Setup\n");

    const state = await loadState();
    let commandContext = await chooseCommandInteractively(parsed, state);
    commandContext = await completeMissingArgs(commandContext, state);

    if (!commandContext.command) {
        printHelp();
        return;
    }

    const commandDefinition = COMMAND_DEFINITIONS[commandContext.command];

    if (commandDefinition.requiredCommands.length > 0) {
        info("Checking dependencies...");
        ensureCommandsAvailable(commandDefinition.requiredCommands);
        ok(`Dependencies available: ${dependencySummary(commandDefinition.name)}`);
    }

    if (commandDefinition.requiresClusterContext) {
        state.lastContext = await ensureClusterContext(commandContext.context || state.lastContext);
        await saveState(state);
    }

    let helmRoot: string | null = null;
    if (commandDefinition.requiresHelmCache) {
        helmRoot = await ensureHelmCache(commandContext.refreshHelm);
    }

    await commandDefinition.execute({
        parsed: commandContext,
        state,
        helmRoot,
    });
}

export function closeCli(): void {
    closePromptInterface();
}
