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
 * in the returned map, and every stage can be repeated safely, so a new leader resumes the roll where it was.
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
        val roll = Roll(state, phase, stagesFor(groupIds), options)
        return when (val stage = state[STAGE] ?: START) {
            START -> roll.startPhase(groupIds)
            PLAN -> roll.plan()
            REPLACE -> roll.replace()
            WAIT_NEW -> roll.waitForReplacements()
            STOP -> roll.stopOld()
            WAIT_OLD -> roll.waitForOldGone()
            else -> RollResult.Failed("Unknown roll stage $stage")
        }
    }

    private fun stagesFor(groupIds: List<GroupId>) =
        if (groupIds.any { groups.find(it)?.value?.type == GroupType.STATIC }) STOP_FIRST else REPLACE_FIRST

    /** One step of one phase; [stages] is the order of the stages each batch goes through. */
    private inner class Roll(
        private val state: Map<String, String>,
        private val phase: Int,
        private val stages: List<String>,
        private val options: RollOptions,
    ) {
        private val label = "phase ${phase + 1}"

        fun startPhase(groupIds: List<GroupId>): RollResult {
            val queue =
                groupIds.flatMap { groupId ->
                    servers
                        .listByGroup(
                            groupId,
                        ).map { it.value }
                        .filter { it.state != ServerState.STOPPING }
                        .map { entry(groupId.value, it.id) }
                }
            return nextBatch(state + (QUEUE to queue.joinedList()))
        }

        /** The replacement ids are saved before any is requested, so a repeated step requests the same servers. */
        fun plan(): RollResult {
            val planned = state.list(BATCH).map { entry(groupOf(it), ids.next()) }
            return running(
                state + mapOf(NEW to planned.joinedList(), STAGE to REPLACE),
                "$label: replacing ${planned.size} servers",
            )
        }

        fun replace(): RollResult {
            val requested =
                state.list(NEW).mapNotNull { planned ->
                    val group = groups.find(GroupId(groupOf(planned)))?.value ?: return@mapNotNull null
                    val server = lifecycle.request(idOf(planned), group.id, group.type, group.profileId, options.actor)
                    // A STATIC group's only server may have been started meanwhile (autoscaler, admin): it is the replacement.
                    val id =
                        server?.id ?: occupant(group.id)
                            ?: return RollResult.Running(
                                state,
                                phase,
                                "$label: waiting for ${group.id} to free its volume",
                            )
                    entry(group.id.value, id)
                }
            return advance(
                state + (NEW to requested.joinedList()),
                REPLACE,
                "$label: waiting for ${requested.size} replacements",
            )
        }

        fun waitForReplacements(): RollResult {
            val pending = state.list(NEW)
            pending.forEach { planned ->
                val group = GroupId(groupOf(planned))
                if (servers.get(idOf(planned)) == null ||
                    failures.isPaused(group)
                ) {
                    return RollResult.Failed("A replacement server of group $group did not start")
                }
            }
            if (pending.all {
                    servers.get(idOf(it))?.value?.state == ServerState.RUNNING
                }
            ) {
                return advance(state, WAIT_NEW, "$label: replacements running")
            }
            if (waited() >
                REPLACEMENT_TIMEOUT
            ) {
                return RollResult.Failed("Replacements of $label did not start within $REPLACEMENT_TIMEOUT")
            }
            return running(state, "$label: waiting for replacements")
        }

        fun stopOld(): RollResult {
            stopEach(state.list(BATCH).map(::idOf))
            return advance(state, STOP, "$label: stopping old servers")
        }

        fun waitForOldGone(): RollResult {
            val remaining = state.list(BATCH).map(::idOf).mapNotNull { servers.get(it)?.value }
            if (remaining.isEmpty()) return advance(state, WAIT_OLD, "$label: old servers stopped")
            // The autoscaler may have brought a draining server back; it has to go again.
            stopEach(remaining.filter { it.state == ServerState.RUNNING }.map { it.id })
            val limit = if (options.graceful) GRACEFUL_STOP_TIMEOUT else STOP_TIMEOUT
            if (waited() > limit) return RollResult.Failed("Old servers of $label did not stop within $limit")
            return running(state, "$label: waiting for ${remaining.size} old servers to stop")
        }

        private fun stopEach(ids: List<UUID>) =
            ids.forEach { id ->
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

        private fun nextBatch(state: Map<String, String>): RollResult {
            val queue = state.list(QUEUE)
            if (queue.isEmpty()) {
                return RollResult.Running(
                    mapOf(PHASE to (phase + 1).toString(), STAGE to START),
                    phase + 1,
                    "$label done",
                )
            }
            val batch = queue.take(options.batchSize)
            val next =
                state +
                    mapOf(
                        QUEUE to queue.drop(batch.size).joinedList(),
                        BATCH to batch.joinedList(),
                        NEW to "",
                        STAGE to stages.first(),
                    )
            return running(next, "$label: ${batch.size} servers next")
        }

        /** Moves to the stage after [current], or to the next batch after the last one. */
        private fun advance(
            state: Map<String, String>,
            current: String,
            message: String,
        ): RollResult {
            val nextStage = stages.getOrNull(stages.indexOf(current) + 1) ?: return nextBatch(state)
            return running(state + mapOf(STAGE to nextStage, SINCE to clock.millis().toString()), message)
        }

        private fun running(
            state: Map<String, String>,
            message: String,
        ) = RollResult.Running(state, phase, message)

        private fun waited(): Duration =
            Duration.between(Instant.ofEpochMilli(state[SINCE]?.toLong() ?: clock.millis()), clock.instant())
    }

    private fun occupant(groupId: GroupId): UUID? =
        servers
            .listByGroup(groupId)
            .map { it.value }
            .firstOrNull {
                it.state !=
                    ServerState.STOPPING
            }?.id

    private fun entry(
        groupId: String,
        id: UUID,
    ) = "$groupId$SEPARATOR$id"

    private fun groupOf(entry: String) = entry.substringBefore(SEPARATOR)

    private fun idOf(entry: String): UUID = UUID.fromString(entry.substringAfter(SEPARATOR))

    private companion object {
        const val PHASE = "phase"
        const val STAGE = "stage"
        const val QUEUE = "queue"
        const val BATCH = "batch"
        const val NEW = "new"
        const val SINCE = "since"
        const val SEPARATOR = "/"
        const val REASON = "restart"

        const val START = "start"
        const val PLAN = "plan"
        const val REPLACE = "replace"
        const val WAIT_NEW = "wait-new"
        const val STOP = "stop"
        const val WAIT_OLD = "wait-old"

        // PLAN must precede REPLACE in both orders: replacement ids are fixed before anything is requested.
        val REPLACE_FIRST = listOf(PLAN, REPLACE, WAIT_NEW, STOP, WAIT_OLD)
        val STOP_FIRST = listOf(STOP, WAIT_OLD, PLAN, REPLACE, WAIT_NEW)

        val REPLACEMENT_TIMEOUT: Duration = Duration.ofMinutes(10)
        val STOP_TIMEOUT: Duration = Duration.ofMinutes(10)

        /** Proxies only stop once their players left; a roll that waits longer fails instead of blocking all others. */
        val GRACEFUL_STOP_TIMEOUT: Duration = Duration.ofHours(2)
    }
}
