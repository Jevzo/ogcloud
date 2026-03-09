export interface BotConfig {
    readonly token: string;
    readonly onlineAnnouncementChannelId: string;
    readonly onlineAnnouncementMessage: string;
    readonly supportRoleId: string;
    readonly bugReportsChannelId: string;
    readonly suggestionsChannelId: string;
    readonly allowedSupportCommandRoleIds: readonly string[];
}

function getRequiredEnv(name: "DISCORD_BOT_TOKEN"): string {
    const value = Bun.env[name] ?? process.env[name];

    if (!value || value.trim().length === 0) {
        throw new Error(`Missing required environment variable: ${name}`);
    }

    return value;
}

export const config: BotConfig = {
    token: getRequiredEnv("DISCORD_BOT_TOKEN"),
    onlineAnnouncementChannelId: "1478210073854480555",
    onlineAnnouncementMessage:
        "☁️ Support Bot is now online! Use /support to get or remove the support role.",
    supportRoleId: "1478210072147398876",
    bugReportsChannelId: "1478210073175003151",
    suggestionsChannelId: "1478210073175003150",
    allowedSupportCommandRoleIds: [
        "1478210072160243814",
        "1478210072160243813",
        "1478210072160243812",
    ],
};
