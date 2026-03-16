package io.ogwars.cloud.controller.service

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class RuntimeBundleBootstrap(
    private val runtimeBundleService: RuntimeBundleService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        runtimeBundleService.bootstrapAll()
    }
}
