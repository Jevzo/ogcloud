package io.ogwars.cloud.service

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.ClassPathResource

/** Adds the shared service defaults with the lowest precedence, so every other source overrides them. */
class ServiceDefaults : EnvironmentPostProcessor {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        YamlPropertySourceLoader()
            .load(
                SOURCE_NAME,
                ClassPathResource(FILE),
            ).forEach(environment.propertySources::addLast)
    }

    private companion object {
        const val FILE = "ogcloud-service-defaults.yaml"
        const val SOURCE_NAME = "ogcloud-service-defaults"
    }
}
