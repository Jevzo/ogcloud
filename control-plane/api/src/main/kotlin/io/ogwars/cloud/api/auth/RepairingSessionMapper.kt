package io.ogwars.cloud.api.auth

import org.slf4j.LoggerFactory
import org.springframework.session.MapSession
import org.springframework.session.data.redis.RedisSessionMapper
import java.util.function.BiFunction

/**
 * Reads login sessions as Spring does, but treats a hash that lost its creation time as gone and [delete]s it. A
 * request that loaded a session before its id changed at sign-in can write the old id's attributes back after the
 * rename; Spring would then fail every request carrying that cookie and every token exchange of its user.
 */
class RepairingSessionMapper(
    private val delete: (String) -> Unit,
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
            log.warn("Removing login session {}, which is incomplete: {}", id, incomplete.message)
            delete(id)
            null
        }
}
