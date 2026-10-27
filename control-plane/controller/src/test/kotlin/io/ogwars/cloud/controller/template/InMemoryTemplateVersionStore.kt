package io.ogwars.cloud.controller.template

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.TemplateVersionStore
import io.ogwars.cloud.domain.template.TemplateVersion
import java.util.UUID

class InMemoryTemplateVersionStore : TemplateVersionStore {
    private val versions = mutableMapOf<UUID, TemplateVersion>()

    override fun add(version: TemplateVersion) {
        versions[version.id] = version
    }

    override fun findByGroup(groupId: GroupId) =
        versions.values
            .filter {
                it.groupId == groupId
            }.sortedByDescending { it.createdAt }

    override fun find(id: UUID) = versions[id]

    override fun delete(id: UUID) {
        versions.remove(id)
    }
}
