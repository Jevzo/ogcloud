package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.PodEnv
import io.ogwars.cloud.sdk.GroupType

/** Which server this is, from the environment the controller gave the pod. */
data class NodeIdentity(
    val serverId: String,
    val groupId: String,
    val groupType: GroupType,
    /** The group's players per server (D-0134). */
    val maxPlayers: Int,
) {
    companion object {
        @JvmStatic
        fun fromEnvironment(environment: Map<String, String>): NodeIdentity {
            fun required(name: String) =
                environment[name]?.takeIf(String::isNotBlank) ?: throw IllegalStateException("$name is not set")
            return NodeIdentity(
                required(PodEnv.SERVER_ID),
                required(PodEnv.GROUP_ID),
                GroupType.valueOf(required(PodEnv.GROUP_TYPE)),
                required(PodEnv.MAX_PLAYERS).toInt(),
            )
        }
    }
}
