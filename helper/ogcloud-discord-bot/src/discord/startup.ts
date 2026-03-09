import type { Client } from "discord.js";

import type { BotConfig } from "../config";
import type { SlashCommand } from "../types/slash-command";

function ensureGuildTextChannel(
    channel: Awaited<ReturnType<Client["channels"]["fetch"]>>,
    channelId: string,
) {
    if (!channel) {
        throw new Error(`Channel ${channelId} was not found.`);
    }

    if (!channel.isTextBased() || channel.isDMBased()) {
        throw new Error(`Channel ${channelId} must be a guild text-based channel.`);
    }

    return channel;
}

export async function syncGuildSlashCommands(
    client: Client,
    config: BotConfig,
    commandMap: ReadonlyMap<string, SlashCommand>,
): Promise<number> {
    const onlineChannel = ensureGuildTextChannel(
        await client.channels.fetch(config.onlineAnnouncementChannelId),
        config.onlineAnnouncementChannelId,
    );

    const guild = onlineChannel.guild;
    const commandDefinitions = [...commandMap.values()].map((command) => command.data.toJSON());

    const registeredCommands = await guild.commands.set(commandDefinitions);
    return registeredCommands.size;
}

export async function sendOnlineAnnouncement(client: Client, config: BotConfig): Promise<void> {
    const channel = ensureGuildTextChannel(
        await client.channels.fetch(config.onlineAnnouncementChannelId),
        config.onlineAnnouncementChannelId,
    );

    await channel.send(config.onlineAnnouncementMessage);
}
