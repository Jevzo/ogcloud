package io.ogwars.cloud.node.client

import io.grpc.CallCredentials
import io.grpc.Metadata
import io.grpc.SecurityLevel
import io.grpc.Status
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor

/**
 * Sends the pod's projected token as a bearer token on every call (D-0013). The kubelet rotates the file well
 * before the token expires, so it is reread every minute; it never travels without TLS.
 */
class TokenCredentials(
    private val file: Path,
    private val clock: Clock,
    private val read: (Path) -> String = Files::readString,
) : CallCredentials() {
    private var cached: Pair<String, Instant>? = null

    override fun applyRequestMetadata(
        requestInfo: RequestInfo,
        executor: Executor,
        applier: MetadataApplier,
    ) {
        if (requestInfo.securityLevel != SecurityLevel.PRIVACY_AND_INTEGRITY) {
            applier.fail(Status.UNAUTHENTICATED.withDescription("the pod token is only sent over TLS"))
            return
        }
        executor.execute {
            try {
                applier.apply(Metadata().apply { put(AUTHORIZATION, "Bearer ${token()}") })
            } catch (e: IOException) {
                applier.fail(Status.UNAVAILABLE.withDescription("cannot read the token $file").withCause(e))
            }
        }
    }

    @Synchronized
    private fun token(): String {
        val now = clock.instant()
        cached?.let { (token, readAt) -> if (Duration.between(readAt, now) < REREAD) return token }
        val token = read(file).trim()
        if (token.isEmpty()) throw IOException("the token file $file is empty")
        cached = token to now
        return token
    }

    private companion object {
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        val REREAD: Duration = Duration.ofMinutes(1)
    }
}
