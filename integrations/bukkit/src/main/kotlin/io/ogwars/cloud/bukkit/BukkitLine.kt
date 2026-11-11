package io.ogwars.cloud.bukkit

import io.ogwars.cloud.text.Color
import io.ogwars.cloud.text.RichText
import org.bukkit.Location
import org.bukkit.command.CommandSender
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

    /** Sends text to a player or the console: components on the Paper line, nearest-colour legacy text on 1.8 (D-0204). */
    fun send(
        to: CommandSender,
        text: RichText,
    )

    /** Shows a permission group on its tablist team: its tab prefix, and the colour its name format shows names in. */
    fun styleTeam(
        team: Team,
        prefix: RichText,
        nameColor: Color?,
    )
}
