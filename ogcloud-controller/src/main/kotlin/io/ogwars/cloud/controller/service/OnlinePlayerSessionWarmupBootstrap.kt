package io.ogwars.cloud.controller.service

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class OnlinePlayerSessionWarmupBootstrap(
    private val playerTrackingService: PlayerTrackingService
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        playerTrackingService.warmupOnlinePlayerSessions()
    }
}
