package io.ogwars.cloud.service

import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.common.UuidV7Generator
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/** New random secrets, e.g. forwarding tokens; injected so tests are deterministic. */
fun interface SecretGenerator {
    fun next(): String
}

/** Time, ids and secrets for every component of a service (D-0088); tests replace them (AGENTS.md §10). */
@AutoConfiguration
class ServiceSupportAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun clock(): Clock = Clock.systemUTC()

    /** The one source of cryptographic randomness, e.g. for nonces. */
    @Bean
    @ConditionalOnMissingBean
    fun secureRandom(): SecureRandom = SecureRandom()

    @Bean
    @ConditionalOnMissingBean
    fun idGenerator(
        clock: Clock,
        random: SecureRandom,
    ): IdGenerator = UuidV7Generator(clock, random)

    @Bean
    @ConditionalOnMissingBean
    fun secretGenerator(random: SecureRandom): SecretGenerator =
        SecretGenerator {
            val bytes = ByteArray(SECRET_BYTES).also(random::nextBytes)
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

    private companion object {
        const val SECRET_BYTES = 32
    }
}
