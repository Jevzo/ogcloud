package io.ogwars.cloud.domain.template

import io.ogwars.cloud.domain.group.GroupId
import java.util.UUID

/** Where template archives live in object storage; API uploads and agent pushes follow the same layout (D-0081). */
object TemplateKeys {
    /** Every template object starts with this. */
    const val PREFIX = "templates/"
    private const val ARCHIVE = ".tar.gz"

    fun of(
        groupId: GroupId,
        id: UUID,
    ): String = "$PREFIX${groupId.value}/$id$ARCHIVE"

    /** No dot segments or extra slashes, so a version can never point into another group's prefix. */
    fun belongsTo(
        groupId: GroupId,
        key: String,
    ): Boolean =
        Regex(
            "^${Regex.escape(
                "$PREFIX${groupId.value}/",
            )}[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}${Regex.escape(ARCHIVE)}$",
        ).matches(key)

    fun describe(groupId: GroupId): String = "$PREFIX${groupId.value}/<uuid>$ARCHIVE"
}
