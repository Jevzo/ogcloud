package io.ogwars.cloud.domain.template

import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.StaleUpdateException
import java.util.UUID

/**
 * Makes a version a group's active template (D-0042). Only that one field changes, so a concurrent edit of the
 * group is reread and the promotion applied on top instead of failing.
 */
class TemplatePromotion(
    private val groups: GroupStore,
) {
    fun promote(
        groupId: GroupId,
        versionId: UUID,
    ): Group {
        repeat(MAX_ATTEMPTS) {
            val current = groups.find(groupId) ?: throw NoSuchElementException("Group $groupId no longer exists")
            val updated = current.value.copy(activeTemplateVersionId = versionId)
            try {
                groups.update(current.revision, updated)
                return updated
            } catch (_: StaleUpdateException) {
                // Someone changed the group meanwhile; reread it.
            }
        }
        throw IllegalStateException("Group $groupId kept changing; the promotion of $versionId gave up")
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
