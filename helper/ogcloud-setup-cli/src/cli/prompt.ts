import * as readline from "node:readline";

import { COLORS } from "../shared/constants";
import { color, warn } from "./output";
import type { AskInputOptions, Choice } from "../shared/types";

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

export async function askInput(message: string, options: AskInputOptions = {}): Promise<string> {
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

export async function askSelect<T>(
    message: string,
    choices: Array<Choice<T>>,
    defaultIndex = 0,
): Promise<T> {
    if (choices.length === 0) {
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

    return choices[Number.parseInt(value, 10) - 1].value;
}

export async function askConfirm(message: string, defaultYes = true): Promise<boolean> {
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

export function closePromptInterface(): void {
    if (promptInterface) {
        promptInterface.close();
        promptInterface = null;
    }
}
