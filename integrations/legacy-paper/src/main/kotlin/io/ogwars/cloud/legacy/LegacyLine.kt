package io.ogwars.cloud.legacy

import io.ogwars.cloud.bukkit.BukkitLine
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.node.core.text.storedText
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.TextField
import io.ogwars.cloud.text.firstColor
import io.ogwars.cloud.text.legacyCode
import io.ogwars.cloud.text.toLegacy
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scoreboard.Team

/** PandaSpigot 1.8.8: one main thread, and teams that colour names only through a short legacy prefix. */
internal class LegacyLine(
    private val plugin: Plugin,
) : BukkitLine {
    override fun global(task: Runnable) {
        if (plugin.server.isPrimaryThread) task.run() else plugin.server.scheduler.runTask(plugin, task)
    }

    override fun player(
        player: Player,
        task: Runnable,
    ) = global(task)

    override fun teleport(
        player: Player,
        to: Location,
    ) = global { player.teleport(to) }

    override fun send(
        to: CommandSender,
        text: RichText,
    ) = to.sendMessage(text.toLegacy())

    /**
     * 1.8 colours a name with the prefix's last code, so the name format's first colour goes last. 1.8 takes 16
     * characters, so gradients take their first colour (D-0204) and the prefix is cut.
     */
    override fun styleTeam(
        team: Team,
        group: PermissionGroup,
    ) {
        team.prefix = teamPrefix(group)
    }

    companion object {
        private const val PREFIX_LIMIT = 16
        private const val SECTION = '§'

        fun teamPrefix(group: PermissionGroup): String {
            val nameColor =
                storedText(group.nameFormat)
                    .fill(TextField.NAME_FORMAT.samples)
                    .firstColor()
                    ?.let { "$SECTION${legacyCode(it)}" }
                    .orEmpty()
            return (storedText(group.tabPrefix).toLegacy(collapseGradients = true) + nameColor)
                .take(PREFIX_LIMIT)
                .removeSuffix(SECTION.toString())
        }
    }
}
