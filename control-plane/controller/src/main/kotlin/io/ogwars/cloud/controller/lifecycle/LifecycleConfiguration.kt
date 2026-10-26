package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.config.LifecycleProperties
import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.controller.kube.PodSpecBuilder
import io.ogwars.cloud.domain.crashloop.CrashLoopPolicy
import io.ogwars.cloud.domain.server.LifecyclePolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class LifecycleConfiguration {
    @Bean
    fun lifecyclePolicy(properties: LifecycleProperties) = LifecyclePolicy(properties.timeouts)

    @Bean
    fun crashLoopPolicy(properties: LifecycleProperties) = CrashLoopPolicy(properties.crashLoop)

    @Bean
    fun podSpecBuilder(properties: PodProperties) = PodSpecBuilder(properties)
}
