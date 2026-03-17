package io.ogwars.cloud.paper.util

import org.bukkit.Server
import java.lang.reflect.Field

object ReflectionUtil {
    fun setMaxPlayers(
        server: Server,
        maxPlayers: Int,
    ) {
        val playerList = server.javaClass.getMethod("getHandle").invoke(server)
        val field =
            findField(playerList.javaClass, "maxPlayers").apply {
                isAccessible = true
            }

        field.setInt(playerList, maxPlayers)
    }

    fun getTps(maxTps: Double = 20.0): Double {
        val minecraftServerClass = Class.forName("net.minecraft.server.v1_8_R3.MinecraftServer")
        val minecraftServer = minecraftServerClass.getMethod("getServer").invoke(null)

        val field =
            findField(minecraftServer.javaClass, "recentTps").apply {
                isAccessible = true
            }

        val recentTps = field.get(minecraftServer) as DoubleArray
        return recentTps.firstOrNull()?.coerceAtMost(maxTps) ?: maxTps
    }

    private fun findField(
        type: Class<*>,
        name: String,
    ): Field {
        var current: Class<*>? = type

        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }

        throw NoSuchFieldException("Field '$name' not found in class hierarchy of ${type.name}")
    }
}
