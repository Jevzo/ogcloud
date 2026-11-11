package io.ogwars.cloud.legacy

import io.ogwars.cloud.bukkit.BukkitLine
import io.ogwars.cloud.text.Color
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.legacyTeamPrefix
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

    /** 1.8 colours a name with the prefix's last code, so the name's colour goes last (D-0204). */
    override fun styleTeam(
        team: Team,
        prefix: RichText,
        nameColor: Color?,
    ) {
        team.prefix = legacyTeamPrefix(prefix, nameColor)
    }
}
