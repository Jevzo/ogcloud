package io.ogwars.cloud.paper.compat

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.bukkit.scoreboard.Team
import java.lang.reflect.Field

object BukkitCompatibility {
    fun applyConfiguredMaxPlayers(
        server: Server,
        maxPlayers: Int,
    ): Boolean =
        invokeIntMethod(server, "setMaxPlayers", maxPlayers) ||
            setIntField(server, "maxPlayers", maxPlayers) ||
            resolveConsole(server)?.let { console ->
                invokeIntMethod(console, "setMaxPlayers", maxPlayers) ||
                    setIntField(console, "maxPlayers", maxPlayers)
            } == true

    fun addTeamEntry(
        team: Team,
        entryName: String,
    ) {
        if (!invokeStringMethod(team, "addEntry", entryName)) {
            invokeObjectMethod(team, "addPlayer", offlinePlayer(entryName), OfflinePlayer::class.java)
        }
    }

    fun removeTeamEntry(
        team: Team,
        entryName: String,
    ) {
        if (!invokeStringMethod(team, "removeEntry", entryName)) {
            invokeObjectMethod(team, "removePlayer", offlinePlayer(entryName), OfflinePlayer::class.java)
        }
    }

    fun teamHasEntry(
        team: Team,
        entryName: String,
    ): Boolean {
        invokeBooleanStringMethod(team, "hasEntry", entryName)?.let { return it }

        val players = invokeNoArgMethod(team, "getPlayers") as? Iterable<*> ?: return false
        return players
            .filterIsInstance<OfflinePlayer>()
            .any { player -> player.name == entryName }
    }

    fun setTeamPrefix(
        team: Team,
        prefix: String,
    ) {
        if (invokeStringMethod(team, "setPrefix", prefix)) {
            return
        }

        val serializerClass =
            runCatching {
                Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer")
            }.getOrNull() ?: return
        val serializer =
            runCatching {
                serializerClass.getMethod("legacySection").invoke(null)
            }.getOrNull() ?: return
        val component =
            runCatching {
                serializerClass
                    .getMethod("deserialize", String::class.java)
                    .invoke(serializer, prefix)
            }.getOrNull() ?: return

        team.javaClass.methods
            .firstOrNull { method -> method.name == "prefix" && method.parameterTypes.size == 1 }
            ?.let { method ->
                runCatching { method.invoke(team, component) }
            }
    }

    private fun offlinePlayer(entryName: String): OfflinePlayer = Bukkit.getOfflinePlayer(entryName)

    private fun resolveConsole(server: Server): Any? = invokeNoArgMethod(server, "getServer")

    private fun invokeNoArgMethod(
        target: Any,
        methodName: String,
    ): Any? =
        target.javaClass.methods
            .firstOrNull { method -> method.name == methodName && method.parameterCount == 0 }
            ?.let { method -> runCatching { method.invoke(target) }.getOrNull() }

    private fun invokeStringMethod(
        target: Any,
        methodName: String,
        value: String,
    ): Boolean = invokeMethod(target, methodName, value, String::class.java)

    private fun invokeObjectMethod(
        target: Any,
        methodName: String,
        value: Any,
        parameterType: Class<*>,
    ): Boolean = invokeMethod(target, methodName, value, parameterType)

    private fun invokeIntMethod(
        target: Any,
        methodName: String,
        value: Int,
    ): Boolean =
        target.javaClass.methods
            .firstOrNull { method ->
                method.name == methodName &&
                    method.parameterCount == 1 &&
                    method.parameterTypes.firstOrNull() in INT_PARAMETER_TYPES
            }?.let { method ->
                runCatching { method.invoke(target, value) }.isSuccess
            } ?: false

    private fun invokeMethod(
        target: Any,
        methodName: String,
        value: Any,
        parameterType: Class<*>,
    ): Boolean =
        runCatching {
            target.javaClass.getMethod(methodName, parameterType).invoke(target, value)
        }.isSuccess

    private fun invokeBooleanStringMethod(
        target: Any,
        methodName: String,
        value: String,
    ): Boolean? =
        runCatching {
            target.javaClass.getMethod(methodName, String::class.java).invoke(target, value) as? Boolean
        }.getOrNull()

    private fun setIntField(
        target: Any,
        fieldName: String,
        value: Int,
    ): Boolean {
        val field = findField(target.javaClass, fieldName) ?: return false
        return runCatching {
            field.isAccessible = true
            field.set(target, value)
        }.isSuccess
    }

    private fun findField(
        type: Class<*>,
        fieldName: String,
    ): Field? {
        var current: Class<*>? = type

        while (current != null) {
            current.declaredFields
                .firstOrNull { field ->
                    field.name == fieldName &&
                        field.type in INT_PARAMETER_TYPES
                }?.let { return it }
            current = current.superclass
        }

        return null
    }

    private val INT_PARAMETER_TYPES =
        setOf(
            Int::class.javaPrimitiveType,
            Int::class.javaObjectType,
        ).filterNotNull()
            .toSet()
}
