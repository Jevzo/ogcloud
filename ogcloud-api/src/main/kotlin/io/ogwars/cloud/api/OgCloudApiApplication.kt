package io.ogwars.cloud.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OgCloudApiApplication

fun main(args: Array<String>) {
    runApplication<OgCloudApiApplication>(*args)
}
