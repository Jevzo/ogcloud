package io.ogwars.cloud.paper

import io.ogwars.cloud.bukkit.BukkitLine
import io.ogwars.cloud.text.Color
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.nearestNamed
import io.ogwars.cloud.text.toComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scoreboard.Team

/** Paper: region-aware schedulers and async teleports, never the Bukkit scheduler, so Folia works too (D-0135). */
internal class PaperLine(
    private val plugin: Plugin,
) : BukkitLine {
    override fun global(task: Runnable) = plugin.server.globalRegionScheduler.execute(plugin, task)

    override fun player(
        player: Player,
        task: Runnable,
    ) {
        player.scheduler.run(plugin, { task.run() }, null)
    }

    override fun teleport(
        player: Player,
        to: Location,
    ) {
        player.teleportAsync(to)
    }

    override fun refreshCommands(player: Player) = player.updateCommands()

    override fun send(
        to: CommandSender,
        text: RichText,
    ) = to.sendMessage(text.toComponent())

    /** The team colour is a named colour only, so names take the nearest one; the prefix keeps every colour. */
    override fun styleTeam(
        team: Team,
        prefix: RichText,
        nameColor: Color?,
    ) {
        team.prefix(prefix.toComponent())
        // White, as names show without a colour, so a team whose format lost its colour drops the old one.
        team.color(nameColor?.nearestNamed() ?: NamedTextColor.WHITE)
    }
}
