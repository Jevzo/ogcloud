package io.ogwars.cloud.api.util

import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.common.model.GroupType

object ServerPresentationSupport {
    private const val NO_TPS = -1.0

    fun resolveMaxPlayers(
        server: ServerDocument,
        configuredGroupMaxPlayers: Int,
        defaultProxyMaxPlayers: Int,
    ): Int {
        if (server.maxPlayers > 0) {
            return server.maxPlayers
        }

        if (server.type != GroupType.PROXY) {
            return 0
        }

        return if (configuredGroupMaxPlayers > 0) {
            configuredGroupMaxPlayers
        } else {
            defaultProxyMaxPlayers
        }
    }

    fun resolveTps(server: ServerDocument): Double =
        if (server.type == GroupType.PROXY) {
            NO_TPS
        } else {
            server.tps
        }
}
