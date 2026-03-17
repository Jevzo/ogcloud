import { spawnSync, type SpawnSyncReturns } from "node:child_process";

import { COMMAND_DOCS } from "../shared/constants";
import type { RequiredCommand, RunCommandOptions } from "../shared/types";

const COMMAND_CHECK_ARGS: Record<RequiredCommand, string[]> = {
    kubectl: ["version", "--client=true"],
    helm: ["version"],
    npm: ["--version"],
    npx: ["--version"],
    node: ["--version"],
};

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

export function runCommand(
    command: string,
    args: string[],
    options: RunCommandOptions = {},
): SpawnSyncReturns<string> {
    const result = spawnSync(resolveExecutable(command), args, {
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

function commandExists(command: string, args: string[]): boolean {
    try {
        const result = spawnSync(resolveExecutable(command), args, {
            encoding: "utf8",
            stdio: "pipe",
        });
        return !(result.error && (result.error as NodeJS.ErrnoException).code === "ENOENT");
    } catch {
        return false;
    }
}

export function ensureCommandsAvailable(commands: readonly RequiredCommand[]): void {
    const uniqueCommands = [...new Set(commands)];
    if (uniqueCommands.length === 0) {
        return;
    }

    const missing = uniqueCommands.filter(
        (command) => !commandExists(command, COMMAND_CHECK_ARGS[command]),
    );
    if (missing.length === 0) {
        return;
    }

    const helpLines = missing
        .map((command) => {
            const docs = COMMAND_DOCS[command];
            return docs ? `- ${command}: ${docs}` : `- ${command}`;
        })
        .join("\n");

    throw new Error(`Missing required dependencies: ${missing.join(", ")}\n${helpLines}`);
}
