import { MessageFlags, SlashCommandBuilder } from "discord.js";

import type { SlashCommand } from "../types/slash-command";

const ACTIVE_STATUS = "active";
const INACTIVE_STATUS = "inactive";

export const supportCommand: SlashCommand = {
    data: new SlashCommandBuilder()
        .setName("support")
        .setDescription("Set your support availability.")
        .addSubcommand((subcommand) =>
            subcommand.setName(ACTIVE_STATUS).setDescription("Set yourself as active for support."),
        )
        .addSubcommand((subcommand) =>
            subcommand
                .setName(INACTIVE_STATUS)
                .setDescription("Set yourself as inactive for support."),
        ),
    async execute({ interaction, config }) {
        if (!interaction.inGuild()) {
            await interaction.reply({
                content: "This command can only be used inside a server.",
                flags: MessageFlags.Ephemeral,
            });
            return;
        }

        const guild = interaction.guild;
        if (!guild) {
            await interaction.reply({
                content: "Could not resolve the guild for this command.",
                flags: MessageFlags.Ephemeral,
            });
            return;
        }

        const member = await guild.members.fetch(interaction.user.id);
        const isAuthorized = config.allowedSupportCommandRoleIds.some((roleId) =>
            member.roles.cache.has(roleId),
        );

        if (!isAuthorized) {
            await interaction.reply({
                content: "You are not allowed to use this command.",
                flags: MessageFlags.Ephemeral,
            });
            return;
        }

        const status = interaction.options.getSubcommand();

        const hasSupportRole = member.roles.cache.has(config.supportRoleId);

        if (status === ACTIVE_STATUS) {
            if (!hasSupportRole) {
                await member.roles.add(
                    config.supportRoleId,
                    "Enabled support mode via /support active",
                );
            }

            await interaction.reply({
                content: "Support mode is now active.",
                flags: MessageFlags.Ephemeral,
            });
            return;
        }

        if (hasSupportRole) {
            await member.roles.remove(
                config.supportRoleId,
                "Disabled support mode via /support inactive",
            );
        }

        await interaction.reply({
            content: "Support mode is now inactive.",
            flags: MessageFlags.Ephemeral,
        });
    },
};
