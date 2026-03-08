package io.ogwars.cloud.paper.permission

import org.bukkit.entity.Player
import org.bukkit.permissions.PermissibleBase
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

object PermissionInjector {

    // Paper 1.21.x with Mojang mappings uses "perm" on CraftHumanEntity.
    private const val FIELD_NAME = "perm"
    private val fieldCache = ConcurrentHashMap<Class<*>, Field>()

    fun inject(player: Player, permissionManager: PermissionManager, logger: Logger) {
        try {
            val field = resolvePermissionField(player.javaClass)
            val current = field.get(player)

            if (current !is CustomPermissibleBase) {
                field.set(player, CustomPermissibleBase(player, permissionManager))
            }
        } catch (exception: Exception) {
            logger.severe("Failed to inject PermissibleBase for ${player.uniqueId}: ${exception.message}")
        }
    }

    fun uninject(player: Player, logger: Logger) {
        try {
            val field = resolvePermissionField(player.javaClass)
            val current = field.get(player)

            if (current is CustomPermissibleBase) {
                field.set(player, PermissibleBase(player))
            }
        } catch (exception: Exception) {
            logger.severe("Failed to uninject PermissibleBase for ${player.uniqueId}: ${exception.message}")
        }
    }

    private fun resolvePermissionField(playerClass: Class<*>): Field {
        return fieldCache.computeIfAbsent(playerClass, ::findPermissionField)
    }

    private fun findPermissionField(playerClass: Class<*>): Field {
        var currentClass: Class<*>? = playerClass

        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(FIELD_NAME).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }

        throw NoSuchFieldException("Could not resolve $FIELD_NAME on ${playerClass.name}")
    }
}
