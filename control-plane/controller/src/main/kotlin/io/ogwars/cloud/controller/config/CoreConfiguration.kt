package io.ogwars.cloud.controller.config

import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.common.UuidV7Generator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import java.time.Clock

/** Time and ids for every component; tests replace them (AGENTS.md §10). */
@Configuration(proxyBeanMethods = false)
class CoreConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun idGenerator(clock: Clock): IdGenerator = UuidV7Generator(clock, SecureRandom())
}
