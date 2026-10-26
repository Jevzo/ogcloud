package io.ogwars.cloud.controller

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ControllerApplication

fun main(args: Array<String>) {
    runApplication<ControllerApplication>(*args)
}
