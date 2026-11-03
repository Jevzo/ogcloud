package io.ogwars.cloud.bukkit

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team

/** What differs between the Paper line and 1.8 (PandaSpigot) for the shared backend code (D-0138). */
interface BukkitLine {
    /** Work that touches the whole server, e.g. console commands and every player's scoreboard. */
    fun global(task: Runnable)

    /** Work on one player. */
    fun player(
        player: Player,
        task: Runnable,
    )

    fun teleport(
        player: Player,
        to: Location,
    )

    /** Sends a player's command tree again after their permissions changed; 1.8 clients have none. */
    fun refreshCommands(player: Player) = Unit

    /** Shows a permission group on its tablist team. */
    fun styleTeam(
        team: Team,
        group: PermissionGroup,
    )
}
