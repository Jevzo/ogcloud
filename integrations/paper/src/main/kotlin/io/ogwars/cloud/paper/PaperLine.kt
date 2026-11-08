package io.ogwars.cloud.paper

import io.ogwars.cloud.bukkit.BukkitLine
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.node.core.text.ColorCodes
import org.bukkit.Location
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

    /** Current clients colour the name with the team colour, not the prefix's last code, so both are set. */
    override fun styleTeam(
        team: Team,
        group: PermissionGroup,
    ) {
        team.prefix(legacyText(group.tabPrefix))
        legacyColor(ColorCodes.lastColor(group.nameColor))?.let(team::color)
    }
}
