package io.ogwars.cloud.controller.config

import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.common.UuidV7Generator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/** New random secrets, e.g. forwarding tokens; injected so tests are deterministic. */
fun interface SecretGenerator {
    fun next(): String
}

/** Time and ids for every component; tests replace them (AGENTS.md §10). */
@Configuration(proxyBeanMethods = false)
class CoreConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun idGenerator(clock: Clock): IdGenerator = UuidV7Generator(clock, SecureRandom())

    @Bean
    fun secretGenerator(): SecretGenerator {
        val random = SecureRandom()
        return SecretGenerator {
            val bytes = ByteArray(SECRET_BYTES).also(random::nextBytes)
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    private companion object {
        const val SECRET_BYTES = 32
    }
}
