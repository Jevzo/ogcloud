package io.ogwars.cloud.controller.service

import io.ogwars.cloud.controller.repository.PermissionGroupRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class PlayerConnectRuntimeStateBootstrap(
    private val playerConnectRuntimeState: PlayerConnectRuntimeState,
    private val permissionGroupRepository: PermissionGroupRepository,
    private val networkSettingsService: NetworkSettingsService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val permissionSystemEnabled = networkSettingsService.findGlobal().general.permissionSystemEnabled
        val groups = permissionGroupRepository.findAll()

        playerConnectRuntimeState.initialize(permissionSystemEnabled, groups)
    }
}
