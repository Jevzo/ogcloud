package io.ogwars.cloud.api.audit

import io.ogwars.cloud.domain.audit.Actor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class AuditConfiguration(
    private val interceptor: AuditInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**")
    }
}

@Configuration(proxyBeanMethods = false)
class ActorConfiguration {
    // TODO(plan: phase-03-api) T-0302 replaces this with the authenticated user or OAuth client.
    @Bean
    fun currentActor(): CurrentActor = CurrentActor { Actor.system("anonymous") }
}
