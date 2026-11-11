package io.ogwars.cloud.bukkit

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.node.core.text.storedText
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.toLegacy
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team

/** Runs everything at once on the test thread, with legacy text and prefixes as 1.8 has them. */
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

    @Suppress("DEPRECATION") // The 1.8 API the shared code targets takes only strings.
    override fun send(
        to: CommandSender,
        text: RichText,
    ) = to.sendMessage(text.toLegacy())

    override fun styleTeam(
        team: Team,
        group: PermissionGroup,
    ) {
        @Suppress("DEPRECATION") // The 1.8 API the shared code targets has only string prefixes.
        team.prefix = storedText(group.tabPrefix).toLegacy()
    }
}
