package io.ogwars.cloud.node.core.routing

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.RoutingStrategy
import io.ogwars.cloud.node.core.state.Backend
import io.ogwars.cloud.node.core.state.NetworkState
import io.ogwars.cloud.node.core.state.ServerDirectory
import io.ogwars.cloud.node.core.text.Message
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Where a player should go: a server, or a group to pick one from. */
sealed interface Destination {
    data class Server(
        val backend: Backend,
    ) : Destination

    data class Group(
        val groupId: String,
        /** Null uses the network's strategy. */
        val strategy: RoutingStrategy? = null,
    ) : Destination
}

/** The outcome of routing a player. */
sealed interface Route {
    data class To(
        val backend: Backend,
    ) : Route

    data class Kick(
        val reason: Message,
    ) : Route

    /** No backend of the group has room; a player already on a backend stays there (D-0176). */
    data object NoRoom : Route
}

/**
 * Picks backends for players on a proxy, as v1: a group in maintenance takes only players with the bypass, others go
 * to the default group or are kicked; full and draining backends take nobody from a group route, while a server
 * named explicitly is taken as asked and its own limit decides; STATIC groups have one server.
 */
class Router(
    private val directory: ServerDirectory,
    private val network: NetworkState,
) {
    private val turns = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Routes a player to [destination]. [bypass] is whether they may enter groups in maintenance; [full] names the
     * backends that just turned players away as full; [players] counts a backend's players across the network
     * (D-0142).
     */
    fun route(
        destination: Destination,
        bypass: Boolean,
        full: Set<String> = emptySet(),
        players: (Backend) -> Int,
    ): Route {
        val groupId =
            when (destination) {
                is Destination.Server -> destination.backend.groupId
                is Destination.Group -> destination.groupId
            }
        if (network.inMaintenance(groupId) && !bypass) return fallback(groupId, full, players)
        val backend =
            when (destination) {
                is Destination.Server -> destination.backend
                is Destination.Group -> pick(groupId, destination.strategy, full, players)
            }
        return backend?.let(Route::To) ?: Route.NoRoom
    }

    /**
     * Another backend of [refused]'s group for a player it turned away. A backend checks its own limit only at the end
     * of a switch, when the player has already left their old server (D-0142).
     */
    fun instead(
        refused: Backend,
        bypass: Boolean,
        full: Set<String> = emptySet(),
        players: (Backend) -> Int,
    ): Route = route(Destination.Group(refused.groupId), bypass, full + refused.id, players)

    /** Where a joining player goes first: the default group. */
    fun initial(
        bypass: Boolean,
        full: Set<String> = emptySet(),
        players: (Backend) -> Int,
    ): Route = route(Destination.Group(network.settings.defaultGroupId), bypass, full, players)

    private fun fallback(
        blocked: String,
        full: Set<String>,
        players: (Backend) -> Int,
    ): Route {
        val defaultGroup = network.settings.defaultGroupId
        if (defaultGroup == blocked ||
            network.inMaintenance(defaultGroup)
        ) {
            return Route.Kick(Message.KICK_GROUP_MAINTENANCE)
        }
        return pick(defaultGroup, strategy = null, full, players)?.let(Route::To) ?: Route.NoRoom
    }

    private fun pick(
        groupId: String,
        strategy: RoutingStrategy?,
        full: Set<String>,
        players: (Backend) -> Int,
    ): Backend? {
        val open =
            directory
                .ofGroup(groupId)
                .filter { it.id !in full && !it.draining && (it.maxPlayers == 0 || players(it) < it.maxPlayers) }
                .sortedBy { it.displayName }
        if (open.size <= 1 || network.groupType(groupId) == GroupType.GROUP_TYPE_STATIC) return open.firstOrNull()
        return when (strategy ?: network.settings.routingStrategy) {
            RoutingStrategy.ROUTING_STRATEGY_LEAST_FILLED -> {
                open.minBy(players)
            }

            RoutingStrategy.ROUTING_STRATEGY_MOST_FILLED -> {
                open.maxBy(players)
            }

            RoutingStrategy.ROUTING_STRATEGY_ROUND_ROBIN -> {
                open[Math.floorMod(turns.computeIfAbsent(groupId) { AtomicInteger() }.getAndIncrement(), open.size)]
            }

            // BALANCED, and an unset strategy as in v1, balance by load.
            else -> {
                open.minBy { load(it, players) }
            }
        }
    }

    private fun load(
        backend: Backend,
        players: (Backend) -> Int,
    ): Double =
        if (backend.maxPlayers >
            0
        ) {
            players(backend).toDouble() / backend.maxPlayers
        } else {
            players(backend).toDouble()
        }
}
