package io.ogwars.cloud.bukkit

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team

/** Runs everything at once on the test thread, styling teams with legacy prefixes as 1.8 does. */
class InlineLine : BukkitLine {
    override fun global(task: Runnable) = task.run()

    override fun player(
        player: Player,
        task: Runnable,
    ) = task.run()

    override fun teleport(
        player: Player,
        to: Location,
    ) {
        player.teleport(to)
    }

    override fun styleTeam(
        team: Team,
        group: PermissionGroup,
    ) {
        @Suppress("DEPRECATION") // The 1.8 API the shared code targets has only string prefixes.
        team.prefix = BukkitPlatform.legacy(group.tabPrefix)
    }
}
