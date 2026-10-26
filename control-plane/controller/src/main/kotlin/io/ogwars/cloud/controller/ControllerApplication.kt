package io.ogwars.cloud.controller

import io.ogwars.cloud.persistence.SchemaVersionProbe
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@ConfigurationPropertiesScan
@Import(SchemaVersionProbe::class)
class ControllerApplication

fun main(args: Array<String>) {
    runApplication<ControllerApplication>(*args)
}
