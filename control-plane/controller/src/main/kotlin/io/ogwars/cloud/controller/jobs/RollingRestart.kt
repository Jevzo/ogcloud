package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.controller.lifecycle.LifecycleService
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.ServerStateStore
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface RollResult {
    data class Running(
        val state: Map<String, String>,
        val completedPhases: Int,
        val message: String,
    ) : RollResult

    data object Finished : RollResult

    data class Failed(
        val reason: String,
    ) : RollResult
}

/** How a roll treats the servers it replaces. */
data class RollOptions(
    val actor: Actor,
    val batchSize: Int,
    /** Graceful rolls drain and wait for players to leave; restarts during maintenance stop servers right away. */
    val graceful: Boolean,
) {
    init {
        require(batchSize > 0) { "batchSize must be positive" }
    }
}

/**
 * Replaces the servers of groups phase by phase and batch by batch, one stage per step. Replacements start before
 * the old servers stop, except in phases with a STATIC group, whose single volume forces stop-first. All state is
 * in the returned map, so a new leader resumes the roll where it was.
 */
@Component
class RollingRestart(
    private val servers: ServerStateStore,
    private val groups: GroupStore,
    private val failures: CrashFailureStore,
    private val lifecycle: LifecycleService,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    fun step(
        phases: List<List<GroupId>>,
        state: Map<String, String>,
        options: RollOptions,
    ): RollResult {
        val phase = state[PHASE]?.toInt() ?: 0
        if (phase >= phases.size) return RollResult.Finished
        val groupIds = phases[phase]
        val order = if (groupIds.any { groups.find(it)?.value?.type == GroupType.STATIC }) STOP_FIRST else REPLACE_FIRST
        return when (val stage = state[STAGE] ?: START) {
            START -> startPhase(groupIds, state, phase, order)
            REPLACE -> replace(state, phase, order, options)
            WAIT_NEW -> waitForReplacements(state, phase, order)
            STOP -> stopOld(state, phase, order, options)
            WAIT_OLD -> waitForOldGone(state, phase, order, options)
            else -> RollResult.Failed("Unknown roll stage $stage")
        }
    }

    private fun startPhase(
        groupIds: List<GroupId>,
        state: Map<String, String>,
        phase: Int,
        order: List<String>,
    ): RollResult {
        val queue =
            groupIds.flatMap { groupId ->
                servers
                    .listByGroup(groupId)
                    .map { it.value }
                    .filter {
                        it.state != ServerState.STOPPING &&
                            it.state != ServerState.STOPPED
                    }.map { entry(groupId, it.id) }
            }
        return nextBatch(state + (QUEUE to queue.joinedList()), phase, order)
    }

    private fun nextBatch(
        state: Map<String, String>,
        phase: Int,
        order: List<String>,
        batchSize: Int = state[BATCH_SIZE_KEY]?.toInt() ?: DEFAULT_BATCH,
    ): RollResult {
        val queue = state.list(QUEUE)
        if (queue.isEmpty()) {
            return RollResult.Running(
                mapOf(PHASE to (phase + 1).toString(), STAGE to START),
                phase + 1,
                "phase ${phase + 1} done",
            )
        }
        val batch = queue.take(batchSize)
        val next =
            state +
                mapOf(
                    QUEUE to queue.drop(batch.size).joinedList(),
                    BATCH to batch.joinedList(),
                    NEW to "",
                    STAGE to order.first(),
                )
        return RollResult.Running(next, phase, "phase ${phase + 1}: replacing ${batch.size} servers")
    }

    private fun replace(
        state: Map<String, String>,
        phase: Int,
        order: List<String>,
        options: RollOptions,
    ): RollResult {
        val replacements =
            state.list(BATCH).mapNotNull { entry ->
                val group = groups.find(GroupId(entry.substringBefore(SEPARATOR)))?.value ?: return@mapNotNull null
                ids
                    .next()
                    .also {
                        lifecycle.request(it, group.id, group.type, group.profileId, options.actor)
                    }.let { entry(group.id, it) }
            }
        return advance(
            state + (NEW to replacements.joinedList()),
            phase,
            order,
            REPLACE,
            options,
            "phase ${phase + 1}: waiting for ${replacements.size} replacements",
        )
    }

    private fun waitForReplacements(
        state: Map<String, String>,
        phase: Int,
        order: List<String>,
    ): RollResult {
        val pending =
            state.list(NEW).map {
                it.substringBefore(SEPARATOR) to
                    UUID.fromString(it.substringAfter(SEPARATOR))
            }
        pending.forEach { (group, id) ->
            if (servers.get(id) == null ||
                failures.isPaused(GroupId(group))
            ) {
                return RollResult.Failed("A replacement server of group $group did not start")
            }
        }
        if (pending.all { (_, id) -> servers.get(id)?.value?.state == ServerState.RUNNING }) {
            return advance(state, phase, order, WAIT_NEW, null, "phase ${phase + 1}: replacements running")
        }
        if (waited(state) >
            REPLACEMENT_TIMEOUT
        ) {
            return RollResult.Failed("Replacements of phase ${phase + 1} did not start within $REPLACEMENT_TIMEOUT")
        }
        return RollResult.Running(state, phase, "phase ${phase + 1}: waiting for replacements")
    }

    private fun stopOld(
        state: Map<String, String>,
        phase: Int,
        order: List<String>,
        options: RollOptions,
    ): RollResult {
        state.list(BATCH).map { UUID.fromString(it.substringAfter(SEPARATOR)) }.forEach { id ->
            if (options.graceful) {
                lifecycle.stop(
                    id,
                    options.actor,
                    REASON,
                )
            } else {
                lifecycle.stopNow(id, options.actor, REASON)
            }
        }
        return advance(state, phase, order, STOP, options, "phase ${phase + 1}: stopping old servers")
    }

    private fun waitForOldGone(
        state: Map<String, String>,
        phase: Int,
        order: List<String>,
        options: RollOptions,
    ): RollResult {
        if (state.list(BATCH).all { servers.get(UUID.fromString(it.substringAfter(SEPARATOR))) == null }) {
            return advance(state, phase, order, WAIT_OLD, options, "phase ${phase + 1}: old servers stopped")
        }
        // A graceful roll waits for players to leave; the deadline sweeper bounds every other stop.
        if (!options.graceful &&
            waited(state) > STOP_TIMEOUT
        ) {
            return RollResult.Failed("Old servers of phase ${phase + 1} did not stop within $STOP_TIMEOUT")
        }
        return RollResult.Running(state, phase, "phase ${phase + 1}: waiting for old servers to stop")
    }

    /** Moves to the stage after [current], or to the next batch after the last one. */
    private fun advance(
        state: Map<String, String>,
        phase: Int,
        order: List<String>,
        current: String,
        options: RollOptions?,
        message: String,
    ): RollResult {
        val nextStage = order.getOrNull(order.indexOf(current) + 1)
        val withBatchSize = options?.let { state + (BATCH_SIZE_KEY to it.batchSize.toString()) } ?: state
        if (nextStage == null) return nextBatch(withBatchSize, phase, order)
        return RollResult.Running(
            withBatchSize + mapOf(STAGE to nextStage, SINCE to clock.millis().toString()),
            phase,
            message,
        )
    }

    private fun waited(state: Map<String, String>): Duration =
        Duration.between(Instant.ofEpochMilli(state[SINCE]?.toLong() ?: clock.millis()), clock.instant())

    private fun entry(
        groupId: GroupId,
        id: UUID,
    ) = "${groupId.value}$SEPARATOR$id"

    private companion object {
        const val PHASE = "phase"
        const val STAGE = "stage"
        const val QUEUE = "queue"
        const val BATCH = "batch"
        const val NEW = "new"
        const val SINCE = "since"
        const val BATCH_SIZE_KEY = "batchSize"
        const val SEPARATOR = "/"
        const val REASON = "restart"
        const val DEFAULT_BATCH = 5

        const val START = "start"
        const val REPLACE = "replace"
        const val WAIT_NEW = "wait-new"
        const val STOP = "stop"
        const val WAIT_OLD = "wait-old"
        val REPLACE_FIRST = listOf(REPLACE, WAIT_NEW, STOP, WAIT_OLD)
        val STOP_FIRST = listOf(STOP, WAIT_OLD, REPLACE, WAIT_NEW)

        val REPLACEMENT_TIMEOUT: Duration = Duration.ofMinutes(10)
        val STOP_TIMEOUT: Duration = Duration.ofMinutes(10)
    }
}
