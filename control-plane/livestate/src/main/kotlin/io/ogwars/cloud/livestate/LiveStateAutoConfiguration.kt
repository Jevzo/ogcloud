package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.LockManager
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.RotatedTokenStore
import io.ogwars.cloud.domain.store.ScalingCooldownStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.SessionStore
import io.lettuce.core.RedisClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** The live-state ports on one shared Valkey connection (D-0074); the service provides the [IdGenerator]. */
@AutoConfiguration
@EnableConfigurationProperties(ValkeyProperties::class)
class LiveStateAutoConfiguration {
    @Bean(destroyMethod = "shutdown")
    fun valkeyClient(properties: ValkeyProperties): RedisClient =
        RedisClient.create(ValkeyConnections.uri(properties.toSettings()))

    @Bean(destroyMethod = "close")
    fun valkeyConnection(client: RedisClient): ValkeyConnection = ValkeyConnections.open(client)

    @Bean
    fun serverStateStore(valkey: ValkeyConnection): ServerStateStore = ValkeyServerStateStore(valkey)

    @Bean
    fun sessionStore(valkey: ValkeyConnection): SessionStore = ValkeySessionStore(valkey)

    @Bean
    fun lockManager(
        valkey: ValkeyConnection,
        ids: IdGenerator,
    ): LockManager = ValkeyLockManager(valkey, ids)

    @Bean
    fun scalingCooldownStore(valkey: ValkeyConnection): ScalingCooldownStore = ValkeyScalingCooldownStore(valkey)

    @Bean
    fun crashFailureStore(valkey: ValkeyConnection): CrashFailureStore = ValkeyCrashFailureStore(valkey)

    @Bean
    fun readModelStore(valkey: ValkeyConnection): ReadModelStore = ValkeyReadModelStore(valkey)

    @Bean
    fun rotatedTokenStore(valkey: ValkeyConnection): RotatedTokenStore = ValkeyRotatedTokenStore(valkey)
}
