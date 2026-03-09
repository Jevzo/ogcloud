import { Client, GatewayIntentBits } from "discord.js";

import { commands, createCommandMap } from "./commands";
import { config } from "./config";
import { registerEventHandlers } from "./discord/event-handlers";

async function startBot(): Promise<void> {
    const client = new Client({
        intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages],
    });

    registerEventHandlers(client, {
        config,
        commandMap: createCommandMap(),
    });

    console.log(`Bootstrapped ${commands.length} command(s).`);
    await client.login(config.token);
}

startBot().catch((error: unknown) => {
    console.error("Failed to start bot.", error);
    process.exit(1);
});
