import type { SlashCommand } from "../types/slash-command";
import { supportCommand } from "./support";

export const commands: readonly SlashCommand[] = [supportCommand];

export function createCommandMap(): ReadonlyMap<string, SlashCommand> {
    return new Map(commands.map((command) => [command.data.name, command]));
}
