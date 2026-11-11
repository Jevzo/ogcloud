package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.common.sha256Hex
import org.slf4j.LoggerFactory
import org.springframework.session.MapSession
import org.springframework.session.data.redis.RedisIndexedSessionRepository
import org.springframework.session.data.redis.RedisSessionMapper
import java.util.function.BiFunction

/**
 * Reads login sessions as Spring does, but treats a hash that lost a required key, such as its creation time, as gone
 * and [remove]s it. A request that loaded a session before its id changed at sign-in could write the old id's
 * attributes back after the rename; Spring would then fail every request with that cookie and every token exchange
 * of its user.
 */
class RepairingSessionMapper(
    private val remove: (String) -> Unit,
) : BiFunction<String, Map<String, Any>, MapSession?> {
    private val log = LoggerFactory.getLogger(javaClass)
    private val spring = RedisSessionMapper()

    override fun apply(
        id: String,
        entries: Map<String, Any>,
    ): MapSession? =
        try {
            spring.apply(id, entries)
        } catch (incomplete: IllegalStateException) {
            // A session id is as good as the cookie, so only a short hash of it is logged.
            log.warn("Removing incomplete login session {}: {}", sha256Hex(id).take(LOGGED_HASH), incomplete.message)
            remove(id)
            null
        }

    companion object {
        private const val LOGGED_HASH = 12

        /** Reads [repository]'s sessions through this mapper; a removal deletes the hash and its expiry key. */
        fun install(
            repository: RedisIndexedSessionRepository,
            namespace: String,
        ) {
            // deleteById cannot remove it: it reads the session through this mapper first and finds none.
            val mapper =
                RepairingSessionMapper { id ->
                    repository.sessionRedisOperations.delete(
                        listOf("$namespace:sessions:$id", "$namespace:sessions:expires:$id"),
                    )
                }
            // Spring's annotations say a mapper never answers null, but the repository reads null as no session,
            // which the repairing mapper in its reference documentation relies on.
            @Suppress("UNCHECKED_CAST")
            repository.setRedisSessionMapper(mapper as BiFunction<String, Map<String, Any>, MapSession>)
        }
    }
}
