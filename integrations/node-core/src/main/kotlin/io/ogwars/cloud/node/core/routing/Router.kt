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
}

/**
 * Picks backends for players on a proxy, as v1: a group in maintenance takes only players with the bypass, others go
 * to the default group or are kicked; full and draining backends take nobody; STATIC groups have one server.
 */
class Router(
    private val directory: ServerDirectory,
    private val network: NetworkState,
) {
    private val turns = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Routes a player to [destination]. [bypass] is whether they may enter groups in maintenance; [players] counts a
     * backend's players as this proxy sees them.
     */
    fun route(
        destination: Destination,
        bypass: Boolean,
        players: (Backend) -> Int,
    ): Route {
        val groupId =
            when (destination) {
                is Destination.Server -> destination.backend.groupId
                is Destination.Group -> destination.groupId
            }
        if (network.inMaintenance(groupId) && !bypass) return fallback(groupId, players)
        val backend =
            when (destination) {
                is Destination.Server -> destination.backend
                is Destination.Group -> pick(groupId, destination.strategy, players)
            }
        return backend?.let(Route::To) ?: Route.Kick(Message.KICK_NO_SERVERS)
    }

    /** Where a joining player goes first: the default group. */
    fun initial(
        bypass: Boolean,
        players: (Backend) -> Int,
    ): Route = route(Destination.Group(network.settings.defaultGroupId), bypass, players)

    private fun fallback(
        blocked: String,
        players: (Backend) -> Int,
    ): Route {
        val defaultGroup = network.settings.defaultGroupId
        if (defaultGroup == blocked ||
            network.inMaintenance(defaultGroup)
        ) {
            return Route.Kick(Message.KICK_GROUP_MAINTENANCE)
        }
        return pick(defaultGroup, strategy = null, players)?.let(Route::To) ?: Route.Kick(Message.KICK_NO_SERVERS)
    }

    private fun pick(
        groupId: String,
        strategy: RoutingStrategy?,
        players: (Backend) -> Int,
    ): Backend? {
        val open =
            directory
                .ofGroup(groupId)
                .filter { !it.draining && (it.maxPlayers == 0 || players(it) < it.maxPlayers) }
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

            // Unset strategies balance by load, v1's default.
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
