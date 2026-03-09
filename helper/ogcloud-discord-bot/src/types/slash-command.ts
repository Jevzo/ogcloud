import type {
    ChatInputCommandInteraction,
    SlashCommandBuilder,
    SlashCommandOptionsOnlyBuilder,
    SlashCommandSubcommandsOnlyBuilder,
} from "discord.js";

import type { BotConfig } from "../config";

export interface SlashCommandContext {
    readonly interaction: ChatInputCommandInteraction;
    readonly config: BotConfig;
}

export interface SlashCommand {
    readonly data:
        | SlashCommandBuilder
        | SlashCommandOptionsOnlyBuilder
        | SlashCommandSubcommandsOnlyBuilder;
    execute(context: SlashCommandContext): Promise<void>;
}
