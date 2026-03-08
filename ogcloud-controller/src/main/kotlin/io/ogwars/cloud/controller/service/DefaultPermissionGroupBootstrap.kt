package io.ogwars.cloud.controller.service

import io.ogwars.cloud.controller.model.PermissionGroupDocument
import io.ogwars.cloud.controller.repository.PermissionGroupRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class DefaultPermissionGroupBootstrap(
    private val permissionGroupRepository: PermissionGroupRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (permissionGroupRepository.count() > 0) {
            return
        }

        val defaultGroup = permissionGroupRepository.save(
            PermissionGroupDocument(
                id = DEFAULT_GROUP_ID,
                name = DEFAULT_GROUP_NAME,
                weight = DEFAULT_WEIGHT,
                default = true,
                permissions = emptyList()
            )
        )

        log.info(
            "Created default permission group: id={}, name={}, permissions={}",
            defaultGroup.id,
            defaultGroup.name,
            defaultGroup.permissions.size
        )
    }

    companion object {
        private const val DEFAULT_GROUP_ID = "default"
        private const val DEFAULT_GROUP_NAME = "Default"
        private const val DEFAULT_WEIGHT = 100
    }
}
