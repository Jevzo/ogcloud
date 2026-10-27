package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.service.SecretGenerator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

/** The BungeeGuard tokens in Infisical (D-0044); proxies use the last one, backends accept all (D-0065). */
interface ForwardingTokenSource {
    fun tokens(): List<String>

    /** Appends [token], so it becomes the one proxies use. */
    fun add(token: String)

    fun remove(token: String)
}

/** The tokens servers currently see: the Secret the Infisical operator syncs into the game namespace. */
fun interface ForwardingSecretReader {
    fun mountedTokens(): List<String>
}

/**
 * Rotates the forwarding token (D-0078): add a new token, wait for the sync, roll the proxies onto it, remove the
 * old tokens, wait for the sync again. Only token fingerprints are stored in the job, never the tokens.
 */
@Component
class ForwardingRotationJob(
    private val source: ObjectProvider<ForwardingTokenSource>,
    private val mounted: ForwardingSecretReader,
    private val secrets: SecretGenerator,
    private val groups: GroupStore,
    private val roll: RollingRestart,
    private val clock: Clock,
) : JobHandler {
    override val kind = JobKinds.FORWARDING_ROTATE

    override fun step(job: Job): JobStep {
        // TODO(D-0078) The Infisical adapter arrives with T-0402; until then a rotation fails right away.
        val tokens =
            source.getIfAvailable() ?: return JobStep.Failed("No forwarding token source is configured (Infisical)")
        val p = job.progress
        return when (p[STAGE] ?: START) {
            START -> {
                continueWith(
                    p + mapOf(STAGE to ADD, OLD to tokens.tokens().map(::fingerprint).joinedList()),
                    0,
                    "recorded the current tokens",
                )
            }

            ADD -> {
                add(tokens, p)
            }

            WAIT_ADD -> {
                waitForAdd(p)
            }

            PROPAGATE -> {
                if (waited(p) >= SECRET_PROPAGATION) {
                    continueWith(p + (STAGE to ROLL), 1, "new token reached the servers")
                } else {
                    continueWith(p, 1, "waiting for kubelets to refresh mounted secrets")
                }
            }

            ROLL -> {
                rollProxies(job, p)
            }

            REMOVE -> {
                removeOld(tokens, p)
            }

            WAIT_REMOVE -> {
                waitForRemove(p)
            }

            else -> {
                JobStep.Failed("Unknown rotation stage ${p[STAGE]}")
            }
        }
    }

    // A crash after adding but before saving progress leaves the token in place; the retry adopts it instead of adding another.
    private fun add(
        tokens: ForwardingTokenSource,
        p: Map<String, String>,
    ): JobStep {
        val old = p.list(OLD).toSet()
        val added = tokens.tokens().filter { fingerprint(it) !in old }
        val token = added.singleOrNull() ?: secrets.next().also(tokens::add)
        return continueWith(
            p + mapOf(STAGE to WAIT_ADD, NEW to fingerprint(token), SINCE to clock.millis().toString()),
            1,
            "added the new token",
        )
    }

    private fun rollProxies(
        job: Job,
        p: Map<String, String>,
    ): JobStep {
        val proxies =
            groups
                .findAll()
                .map {
                    it.value
                }.filter { it.type == GroupType.PROXY }
                .map { it.id }
                .sortedBy { it.value }
                .let { listOf(it) }
        val rollState = p.filterKeys { it.startsWith(ROLL_PREFIX) }.mapKeys { it.key.removePrefix(ROLL_PREFIX) }
        return when (
            val result =
                roll.step(
                    proxies,
                    rollState,
                    RollOptions(job.createdBy, PROXY_BATCH, graceful = true),
                )
        ) {
            is RollResult.Running -> {
                continueWith(
                    p.filterKeys {
                        !it.startsWith(ROLL_PREFIX)
                    } + result.state.mapKeys { ROLL_PREFIX + it.key },
                    2,
                    result.message,
                )
            }

            RollResult.Finished -> {
                continueWith(p + (STAGE to REMOVE), 2, "proxies rolled")
            }

            is RollResult.Failed -> {
                JobStep.Failed(result.reason)
            }
        }
    }

    private fun removeOld(
        tokens: ForwardingTokenSource,
        p: Map<String, String>,
    ): JobStep {
        val old = p.list(OLD).toSet()
        tokens.tokens().filter { fingerprint(it) in old }.forEach(tokens::remove)
        return continueWith(
            p + mapOf(STAGE to WAIT_REMOVE, SINCE to clock.millis().toString()),
            STEPS - 1,
            "removed the old tokens",
        )
    }

    /**
     * The Secret object shows the token first; kubelets update mounted files only after their sync period, so
     * backends are given [SECRET_PROPAGATION] before replacement proxies start using it.
     */
    private fun waitForAdd(p: Map<String, String>): JobStep =
        when {
            fingerprint(p) in mounted.mountedTokens().map(::fingerprint) -> {
                continueWith(p + mapOf(STAGE to PROPAGATE, SINCE to clock.millis().toString()), 1, "new token synced")
            }

            waited(p) > SYNC_TIMEOUT -> {
                JobStep.Failed("The new token did not reach the Kubernetes Secret within $SYNC_TIMEOUT")
            }

            else -> {
                continueWith(p, 1, "waiting for the new token to sync")
            }
        }

    private fun waitForRemove(p: Map<String, String>): JobStep =
        when {
            mounted.mountedTokens().none { fingerprint(it) in p.list(OLD) } -> {
                JobStep.Succeeded("forwarding token rotated")
            }

            waited(p) > SYNC_TIMEOUT -> {
                JobStep.Failed("The old tokens stayed in the Kubernetes Secret for $SYNC_TIMEOUT")
            }

            else -> {
                continueWith(p, STEPS - 1, "waiting for the old tokens to disappear")
            }
        }

    private fun waited(p: Map<String, String>): Duration =
        Duration.between(
            Instant.ofEpochMilli(p[SINCE]?.toLong() ?: clock.millis()),
            clock.instant(),
        )

    private fun continueWith(
        progress: Map<String, String>,
        completed: Int,
        message: String,
    ) = JobStep.Continue(progress, completed, STEPS, message)

    private fun fingerprint(progress: Map<String, String>) = progress[NEW].orEmpty()

    private companion object {
        const val STAGE = "stage"
        const val OLD = "old"
        const val NEW = "new"
        const val ROLL_PREFIX = "roll."
        const val START = "start"
        const val ADD = "add"
        const val WAIT_ADD = "wait-add"
        const val PROPAGATE = "propagate"
        const val SINCE = "since"
        const val ROLL = "roll"
        const val REMOVE = "remove"
        const val WAIT_REMOVE = "wait-remove"
        const val STEPS = 4
        const val PROXY_BATCH = 1
        const val FINGERPRINT_CHARS = 16

        /** Kubelet's sync period plus its secret cache TTL, both one minute by default. */
        val SECRET_PROPAGATION: Duration = Duration.ofMinutes(2)
        val SYNC_TIMEOUT: Duration = Duration.ofMinutes(15)

        fun fingerprint(token: String): String =
            HexFormat
                .of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.toByteArray()),
                ).take(FINGERPRINT_CHARS)
    }
}
