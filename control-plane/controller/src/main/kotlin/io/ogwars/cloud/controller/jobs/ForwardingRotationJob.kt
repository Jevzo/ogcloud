package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.controller.config.SecretGenerator
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.store.GroupStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.security.MessageDigest
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
                if (fingerprint(p) in
                    mounted.mountedTokens().map(::fingerprint)
                ) {
                    continueWith(p + (STAGE to ROLL), 1, "new token synced")
                } else {
                    continueWith(p, 1, "waiting for the new token to sync")
                }
            }

            ROLL -> {
                rollProxies(job, p)
            }

            REMOVE -> {
                removeOld(tokens, p)
            }

            WAIT_REMOVE -> {
                if (mounted.mountedTokens().none { fingerprint(it) in p.list(OLD) }) {
                    JobStep.Succeeded("forwarding token rotated")
                } else {
                    continueWith(
                        p,
                        STEPS - 1,
                        "waiting for the old tokens to disappear",
                    )
                }
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
        return continueWith(p + mapOf(STAGE to WAIT_ADD, NEW to fingerprint(token)), 1, "added the new token")
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
        return continueWith(p + (STAGE to WAIT_REMOVE), STEPS - 1, "removed the old tokens")
    }

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
        const val ROLL = "roll"
        const val REMOVE = "remove"
        const val WAIT_REMOVE = "wait-remove"
        const val STEPS = 4
        const val PROXY_BATCH = 1
        const val FINGERPRINT_CHARS = 16

        fun fingerprint(token: String): String =
            HexFormat
                .of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.toByteArray()),
                ).take(FINGERPRINT_CHARS)
    }
}
