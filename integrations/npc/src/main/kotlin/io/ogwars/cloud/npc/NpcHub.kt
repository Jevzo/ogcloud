package io.ogwars.cloud.npc

import io.ogwars.cloud.contracts.events.v1.ServerState
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.sdk.RoutingStrategy
import com.github.retrooper.packetevents.protocol.player.UserProfile
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import io.ogwars.cloud.contracts.events.v1.RoutingStrategy as ProtoRoutingStrategy
import io.ogwars.cloud.contracts.node.v1.Npc as ManagedNpc
import io.ogwars.cloud.contracts.node.v1.NpcAction as ManagedAction

/** Moves a player into a group, as the SDK's transfers do. */
fun interface GroupTransfer {
    fun transfer(
        player: UUID,
        groupId: String,
        strategy: RoutingStrategy?,
    )
}

/**
 * Every NPC on this server (D-0136): the group's managed NPCs from the read model, and runtime NPCs that plugins
 * create. Each shows to the players near it, turns to players who come close if it should, and runs its clicks.
 * Safe to call from any thread; PacketEvents sends from any thread.
 */
class NpcHub(
    private val packets: NpcPackets,
    private val queries: NodeQueries,
    private val groupId: String,
    private val transfers: GroupTransfer,
    private val scheduler: ScheduledExecutorService,
    private val clock: Clock,
) {
    private val shown = ConcurrentHashMap<String, ShownNpc>()
    private val managed = ConcurrentHashMap<String, ManagedNpc>()
    private val viewers = ConcurrentHashMap<UUID, Viewing>()
    private val counts = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val lastClick = ConcurrentHashMap<UUID, Instant>()
    private val entityIds = AtomicInteger(FIRST_ENTITY_ID)

    /** What one player sees. */
    private class Viewing(
        @Volatile var viewer: NpcViewer,
    ) {
        val npcs: MutableSet<String> = ConcurrentHashMap.newKeySet()

        @Volatile var prepared = false
    }

    /** The group's managed NPCs, as the commands list them. */
    fun managed(): List<ManagedNpc> = managed.values.sortedBy { it.npcId }

    /** A managed NPC by its id or a unique end of it, which `/ogcloud npc` shows as its name. */
    fun findManaged(reference: String): ManagedNpc? =
        managed[reference] ?: managed.values.filter { it.npcId.endsWith(reference, ignoreCase = true) }.singleOrNull()

    /** The managed NPC [from] looks at, within reach. */
    fun lookedAt(from: NpcLocation): ManagedNpc? {
        val yaw = Math.toRadians(from.yaw.toDouble())
        val pitch = Math.toRadians(from.pitch.toDouble())
        val dx = -sin(yaw) * cos(pitch)
        val dy = -sin(pitch)
        val dz = cos(yaw) * cos(pitch)
        return managed.values
            .filter { it.location.world == from.world }
            .map { npc ->
                val toX = npc.location.x - from.x
                val toY = npc.location.y + BODY_CENTER - (from.y + EYE_HEIGHT)
                val toZ = npc.location.z - from.z
                val distance = sqrt(toX * toX + toY * toY + toZ * toZ)
                npc to if (distance == 0.0) 1.0 else (toX * dx + toY * dy + toZ * dz) / distance
            }.filter { (npc, aim) -> aim >= AIM_PRECISION && reach(from, npc) }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Loads the group's managed NPCs, at start and after any NPC update (D-0104). */
    fun reload(): CompletableFuture<Unit> =
        queries.npcs(groupId).thenApply { npcs ->
            val now = npcs.associateBy { it.npcId }
            (managed.keys - now.keys).forEach { id ->
                managed.remove(id)
                remove(MANAGED + id)
            }
            now.values.filter { managed[it.npcId] != it }.forEach { npc ->
                managed[npc.npcId] = npc
                put(managedSpec(npc))
            }
        }

    /** Starts refreshing the live counts the NPCs' text shows. */
    fun start() {
        scheduler.scheduleAtFixedRate({ refreshCounts() }, 0, COUNTS_EVERY.toMillis(), TimeUnit.MILLISECONDS)
    }

    /** A player joined, moved or changed world. */
    fun moved(viewer: NpcViewer) {
        val viewing = viewers.computeIfAbsent(viewer.uuid) { Viewing(viewer) }.also { it.viewer = viewer }
        shown.values.forEach { npc -> sync(viewing, npc) }
    }

    fun left(player: UUID) {
        viewers.remove(player)
        lastClick.remove(player)
    }

    /** A click on an entity; NPCs answer, with a short cooldown against double clicks. */
    fun clicked(
        player: UUID,
        entityId: Int,
        rightClick: Boolean,
    ) {
        val npc = shown.values.firstOrNull { it.bodyId == entityId || entityId in it.lineIds } ?: return
        val now = clock.instant()
        val previous = lastClick.put(player, now)
        if (previous != null && Duration.between(previous, now) < CLICK_COOLDOWN) return
        npc.spec.onClick(player, rightClick)
    }

    /** Shows or replaces an NPC; runtime NPCs come through here too. */
    fun put(spec: NpcSpec) {
        val previous = shown[spec.id]
        val bodyId = previous?.bodyId ?: entityIds.getAndIncrement()
        // The profile name only has to be unique and at most 16 characters; the team hides it (NpcPackets.TEAM).
        val name = previous?.profile?.name ?: (PROFILE_PREFIX + bodyId.toString(Character.MAX_RADIX))
        val profile = UserProfile(previous?.profile?.uuid ?: UUID.randomUUID(), name, NpcPackets.skin(spec))
        val lineIds = lines(spec).indices.map { previous?.lineIds?.getOrNull(it) ?: entityIds.getAndIncrement() }
        val current = ShownNpc(spec, bodyId, profile, lineIds)
        shown[spec.id] = current
        viewers.values.forEach { viewing ->
            if (previous != null && viewing.npcs.remove(spec.id)) packets.despawn(viewing.viewer.user, previous)
            sync(viewing, current)
        }
    }

    fun remove(id: String) {
        val npc = shown.remove(id) ?: return
        viewers.values.filter { it.npcs.remove(id) }.forEach { packets.despawn(it.viewer.user, npc) }
    }

    fun shownIds(): Set<String> = shown.keys.toSet()

    private fun sync(
        viewing: Viewing,
        npc: ShownNpc,
    ) {
        val viewer = viewing.viewer
        val near =
            npc.spec.location.world == viewer.location.world &&
                npc.spec.location.distanceSquared(viewer.location) <= VIEW_DISTANCE * VIEW_DISTANCE
        when {
            near && viewing.npcs.add(npc.spec.id) -> spawn(viewing, npc)
            !near && viewing.npcs.remove(npc.spec.id) -> packets.despawn(viewer.user, npc)
        }
        if (near && npc.spec.lookAtPlayers &&
            npc.spec.location.distanceSquared(viewer.location) <= LOOK_RADIUS * LOOK_RADIUS
        ) {
            val (yaw, pitch) = facing(npc.spec.location, viewer.location)
            packets.look(viewer.user, npc, yaw, pitch)
        }
    }

    private fun spawn(
        viewing: Viewing,
        npc: ShownNpc,
    ) {
        val user = viewing.viewer.user
        if (!viewing.prepared) {
            packets.prepare(user)
            viewing.prepared = true
        }
        packets.spawn(user, npc, lines(npc.spec))
        scheduler.schedule({ packets.unlist(user, npc) }, UNLIST_AFTER.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun refreshCounts() {
        shown.values.mapNotNull { it.spec.countsOf }.toSet().forEach { group ->
            queries.servers(group).thenAccept { servers ->
                val running = servers.filter { it.state == ServerState.SERVER_STATE_RUNNING }
                val now = running.sumOf { it.players } to running.size
                if (counts.put(group, now) != now) rename(group)
            }
        }
    }

    private fun rename(group: String) {
        shown.values.filter { it.spec.countsOf == group }.forEach { npc ->
            val texts = lines(npc.spec)
            viewers.values.filter { npc.spec.id in it.npcs }.forEach { viewing ->
                npc.lineIds.zip(texts).forEach { (id, text) -> packets.rename(viewing.viewer.user, id, text) }
            }
        }
    }

    /** The NPC's non-blank lines with the live counts filled in. */
    private fun lines(spec: NpcSpec): List<String> {
        val (players, servers) = spec.countsOf?.let(counts::get) ?: (0 to 0)
        return listOf(spec.title, spec.subtitle)
            .filter(String::isNotBlank)
            .map { it.replace("%players%", players.toString()).replace("%servers%", servers.toString()) }
    }

    private fun managedSpec(npc: ManagedNpc): NpcSpec {
        val target = actionGroup(npc.rightClick) ?: actionGroup(npc.leftClick)
        return NpcSpec(
            MANAGED + npc.npcId,
            NpcLocation(
                npc.location.world,
                npc.location.x,
                npc.location.y,
                npc.location.z,
                npc.location.yaw,
                npc.location.pitch,
            ),
            npc.title,
            npc.subtitle,
            npc.skinTexture.takeIf(String::isNotEmpty)?.let { NpcSkin(it, npc.skinSignature) },
            npc.lookAtPlayers,
            { player, right -> (if (right) npc.rightClick else npc.leftClick).let { run(player, it) } },
            target,
        )
    }

    private fun run(
        player: UUID,
        action: ManagedAction,
    ) {
        if (!action.hasTransfer()) return
        val strategy =
            action.transfer.routingStrategy.takeIf {
                it != ProtoRoutingStrategy.ROUTING_STRATEGY_UNSPECIFIED
            }
        transfers.transfer(
            player,
            action.transfer.groupId,
            strategy?.let {
                RoutingStrategy.valueOf(it.name.removePrefix("ROUTING_STRATEGY_"))
            },
        )
    }

    private fun actionGroup(action: ManagedAction): String? = action.takeIf { it.hasTransfer() }?.transfer?.groupId

    private fun reach(
        from: NpcLocation,
        npc: ManagedNpc,
    ): Boolean {
        val at = NpcLocation(npc.location.world, npc.location.x, npc.location.y, npc.location.z)
        return at.distanceSquared(from) <= LOOK_REACH * LOOK_REACH
    }

    companion object {
        /** Ids of managed NPCs carry this prefix, so runtime NPCs cannot take them. */
        const val MANAGED = "managed:"
        const val RUNTIME = "runtime:"

        // Fake entities take ids far above the server's own counter.
        private const val FIRST_ENTITY_ID = 1_500_000_000
        private const val PROFILE_PREFIX = "ogcnpc"
        private const val VIEW_DISTANCE = 48.0
        private const val LOOK_RADIUS = 8.0
        private const val LOOK_REACH = 6.0
        private const val EYE_HEIGHT = 1.62
        private const val BODY_CENTER = 0.9
        private const val AIM_PRECISION = 0.97
        private val CLICK_COOLDOWN: Duration = Duration.ofMillis(300)
        private val COUNTS_EVERY: Duration = Duration.ofSeconds(5)

        // 1.8 clients load the skin while the fake player is listed, so it leaves the tab list after a moment.
        private val UNLIST_AFTER: Duration = Duration.ofSeconds(2)

        /** Yaw and pitch that make an NPC at [from] look at the eyes of a player at [to]. */
        fun facing(
            from: NpcLocation,
            to: NpcLocation,
        ): Pair<Float, Float> {
            val dx = to.x - from.x
            val dy = to.y - from.y
            val dz = to.z - from.z
            val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
            val pitch = Math.toDegrees(-atan2(dy, sqrt(dx * dx + dz * dz))).toFloat()
            return yaw to pitch
        }
    }
}
