package io.ogwars.cloud.api.auth

import io.ogwars.cloud.livestate.ValkeyConnections
import io.ogwars.cloud.livestate.ValkeyKeys
import io.ogwars.cloud.livestate.ValkeyProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.session.config.SessionRepositoryCustomizer
import org.springframework.session.data.redis.RedisSessionRepository

/** Login sessions in Valkey, shared by every API replica (D-0091). */
@Configuration(proxyBeanMethods = false)
class SessionConfiguration {
    /** Built from `ogcloud.valkey.*`, so Sentinel, ACL and TLS settings live in one place. */
    @Bean
    fun redisConnectionFactory(properties: ValkeyProperties): LettuceConnectionFactory {
        val settings = properties.toSettings()
        val client =
            LettuceClientConfiguration
                .builder()
                .commandTimeout(settings.timeout)
                .apply { if (settings.tls) useSsl() }
                .build()
        return LettuceConnectionFactory(
            LettuceConnectionFactory.createRedisConfiguration(ValkeyConnections.uri(settings)),
            client,
        )
    }

    @Bean
    fun webSessionNamespace(): SessionRepositoryCustomizer<RedisSessionRepository> =
        SessionRepositoryCustomizer { it.setRedisKeyNamespace(ValkeyKeys.WEB_SESSION_NAMESPACE) }
}
