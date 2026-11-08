package io.ogwars.cloud.legacy

import io.ogwars.cloud.bukkit.BukkitLine
import io.ogwars.cloud.bukkit.BukkitPlatform
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import org.bukkit.Location
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

    /** The name colour goes last, so it colours the name; prefixes are cut to the 16 characters 1.8 clients take. */
    override fun styleTeam(
        team: Team,
        group: PermissionGroup,
    ) {
        team.prefix = teamPrefix(group)
    }

    companion object {
        private const val PREFIX_LIMIT = 16
        private const val SECTION = '§'

        fun teamPrefix(group: PermissionGroup): String =
            BukkitPlatform.legacy(group.tabPrefix + group.nameColor).take(PREFIX_LIMIT).removeSuffix(SECTION.toString())
    }
}
