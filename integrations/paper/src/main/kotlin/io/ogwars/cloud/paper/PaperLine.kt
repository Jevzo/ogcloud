package io.ogwars.cloud.paper

import io.ogwars.cloud.bukkit.BukkitLine
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.node.core.text.storedText
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.TextField
import io.ogwars.cloud.text.firstColor
import io.ogwars.cloud.text.nearestNamed
import io.ogwars.cloud.text.toComponent
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

    /**
     * Current clients colour the name with the team colour, a named colour only, so it is the name format's first
     * colour's nearest one (D-0203); the prefix keeps every colour.
     */
    override fun styleTeam(
        team: Team,
        group: PermissionGroup,
    ) {
        team.prefix(storedText(group.tabPrefix).toComponent())
        storedText(group.nameFormat)
            .fill(TextField.NAME_FORMAT.samples)
            .firstColor()
            ?.let { team.color(it.nearestNamed()) }
    }
}
