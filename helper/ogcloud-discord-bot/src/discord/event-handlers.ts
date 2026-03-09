import {
    type ChatInputCommandInteraction,
    type Client,
    Events,
    type InteractionReplyOptions,
    MessageFlags,
} from "discord.js";

import type { BotConfig } from "../config";
import type { SlashCommand } from "../types/slash-command";
import { sendOnlineAnnouncement, syncGuildSlashCommands } from "./startup";

export interface EventHandlerContext {
    readonly config: BotConfig;
    readonly commandMap: ReadonlyMap<string, SlashCommand>;
}

export function registerEventHandlers(client: Client, context: EventHandlerContext): void {
    client.once(Events.ClientReady, async (readyClient) => {
        console.log(`Logged in as ${readyClient.user.tag}`);

        try {
            const commandCount = await syncGuildSlashCommands(
                readyClient,
                context.config,
                context.commandMap,
            );
            console.log(`Registered ${commandCount} guild slash command(s).`);

            await sendOnlineAnnouncement(readyClient, context.config);
            console.log("Sent startup online announcement.");
        } catch (error: unknown) {
            console.error("Startup setup failed.", error);
        }
    });

    client.on(Events.InteractionCreate, async (interaction) => {
        if (!interaction.isChatInputCommand()) {
            return;
        }

        const command = context.commandMap.get(interaction.commandName);
        if (!command) {
            await safeReply(interaction, "Unknown command.");
            return;
        }

        try {
            await command.execute({
                interaction,
                config: context.config,
            });
        } catch (error: unknown) {
            console.error(`Command "${interaction.commandName}" failed.`, error);
            await safeReply(interaction, "Command failed. Please try again.");
        }
    });

    client.on(Events.MessageCreate, async (message) => {
        if (message.author.bot) {
            return;
        }

        if (
            message.channelId == context.config.bugReportsChannelId ||
            message.channelId == context.config.suggestionsChannelId
        ) {
            await message.react("👍");
            await message.react("👎");
        }
    });
}

async function safeReply(interaction: ChatInputCommandInteraction, content: string): Promise<void> {
    const payload: InteractionReplyOptions = {
        content,
        flags: MessageFlags.Ephemeral,
    };

    if (!interaction.isRepliable()) {
        return;
    }

    if (interaction.replied || interaction.deferred) {
        await interaction.followUp(payload);
        return;
    }

    await interaction.reply(payload);
}
