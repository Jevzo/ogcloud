package io.ogwars.cloud.livestate

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer

/** Same version as the chart's Valkey (D-0021). */
const val VALKEY_IMAGE = "valkey/valkey:9.1.2"
const val VALKEY_PORT = 6379

fun valkeyContainer(): GenericContainer<*> = GenericContainer(VALKEY_IMAGE).withExposedPorts(VALKEY_PORT)

/** A standalone Valkey wired into `ogcloud.valkey.*` for Spring tests of services. */
@TestConfiguration(proxyBeanMethods = false)
class ValkeyContainerConfiguration {
    @Bean
    fun valkey(): GenericContainer<*> = valkeyContainer()

    @Bean
    fun valkeyProperties(valkey: GenericContainer<*>) =
        DynamicPropertyRegistrar { registry ->
            registry.add("ogcloud.valkey.host", valkey::getHost)
            registry.add("ogcloud.valkey.port") { valkey.getMappedPort(VALKEY_PORT) }
        }
}
