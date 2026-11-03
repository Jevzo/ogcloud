package io.ogwars.cloud.paper

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The forwarding tokens backends accept, one per line in the mounted Secret (D-0065). Kubelet swaps the file in
 * place on a rotation (D-0078), so it is read again after [maxAge].
 */
class ForwardingTokens(
    private val file: Path,
    private val clock: Clock,
    private val maxAge: Duration = Duration.ofSeconds(5),
    private val read: (Path) -> String = Files::readString,
) {
    @Volatile
    private var cached: Pair<Set<String>, Instant>? = null

    fun current(): Set<String> {
        val now = clock.instant()
        cached?.let { (tokens, readAt) -> if (Duration.between(readAt, now) < maxAge) return tokens }
        val tokens =
            read(file)
                .lines()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        cached = tokens to now
        return tokens
    }
}
