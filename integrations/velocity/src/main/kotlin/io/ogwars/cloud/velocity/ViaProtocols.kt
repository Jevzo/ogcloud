package io.ogwars.cloud.velocity

import com.velocitypowered.api.proxy.ProxyServer
import com.viaversion.viaversion.api.Via

/**
 * Tells ViaVersion each backend's native protocol as the backend registers (D-0133). Only built when ViaVersion is
 * installed, so its API classes load only then.
 */
internal class ViaProtocols private constructor() {
    fun register(
        server: String,
        protocol: Int,
    ) {
        if (protocol > 0) Via.proxyPlatform().protocolDetectorService().setProtocolVersion(server, protocol)
    }

    fun unregister(server: String) {
        Via.proxyPlatform().protocolDetectorService().uncacheProtocolVersion(server)
    }

    companion object {
        private const val VIAVERSION = "viaversion"

        fun ifInstalled(proxy: ProxyServer): ViaProtocols? =
            if (proxy.pluginManager.isLoaded(VIAVERSION)) ViaProtocols() else null
    }
}
