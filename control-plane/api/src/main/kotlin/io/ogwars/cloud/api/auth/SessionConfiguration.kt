package io.ogwars.cloud.api.auth

import io.ogwars.cloud.livestate.ValkeyConnections
import io.ogwars.cloud.livestate.ValkeyKeys
import io.ogwars.cloud.livestate.ValkeyProperties
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.security.core.session.SessionRegistry
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.session.config.SessionRepositoryCustomizer
import org.springframework.session.data.redis.RedisIndexedSessionRepository
import org.springframework.session.security.SpringSessionBackedSessionRegistry

/** Login sessions in Valkey, shared by every API replica and indexed by user (D-0091, D-0095). */
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
                .clientOptions(ValkeyConnections.clientOptions(settings))
                .apply { if (settings.tls != null) useSsl() }
                .build()
        val configuration = LettuceConnectionFactory.createRedisConfiguration(ValkeyConnections.uri(settings))
        // Spring takes only the Sentinels' password from the URI; they refuse the default user, so it needs ours too.
        if (configuration is RedisSentinelConfiguration) configuration.sentinelUsername = settings.username
        return LettuceConnectionFactory(configuration, client)
    }

    @Bean
    fun webSessionNamespace(): SessionRepositoryCustomizer<RedisIndexedSessionRepository> =
        SessionRepositoryCustomizer { repository ->
            repository.setRedisKeyNamespace(ValkeyKeys.WEB_SESSION_NAMESPACE)
            RepairingSessionMapper.install(repository, ValkeyKeys.WEB_SESSION_NAMESPACE)
        }

    /** Lets OIDC logout find a user's sessions on any replica (D-0095). */
    @Bean
    fun sessionRegistry(sessions: FindByIndexNameSessionRepository<out Session>): SessionRegistry =
        SpringSessionBackedSessionRegistry(sessions)

    companion object {
        // Boot registers Spring Session's filter under this name; servlet prefix patterns also match the bare path.
        private const val SESSION_FILTER_REGISTRATION = "sessionRepositoryFilterRegistration"
        private val SIGN_IN_PATHS = listOf("/login/*", "/oauth2/*", "/webauthn/*", "/.well-known/*")

        /**
         * Only the sign-in paths read, create or extend a login session (D-0220): API calls carry the cookie too and
         * would keep a login session alive while the dashboard is open.
         */
        @Bean
        @JvmStatic
        fun sessionOnlyWhileSigningIn(): BeanPostProcessor =
            object : BeanPostProcessor {
                override fun postProcessAfterInitialization(
                    bean: Any,
                    beanName: String,
                ): Any {
                    if (bean is DelegatingFilterProxyRegistrationBean && beanName == SESSION_FILTER_REGISTRATION) {
                        bean.urlPatterns = SIGN_IN_PATHS
                    }
                    return bean
                }
            }
    }
}
