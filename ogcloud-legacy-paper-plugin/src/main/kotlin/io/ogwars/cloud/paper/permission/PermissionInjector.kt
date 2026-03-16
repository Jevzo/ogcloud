package io.ogwars.cloud.paper.permission

import org.bukkit.entity.Player
import org.bukkit.permissions.PermissibleBase
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

object PermissionInjector {
    private val candidateFieldNames = listOf("perm", "permissibleBase", "permissible")
    private val fieldCache = ConcurrentHashMap<Class<*>, Field>()

    fun inject(
        player: Player,
        permissionManager: PermissionManager,
        logger: Logger,
    ) {
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

    fun uninject(
        player: Player,
        logger: Logger,
    ) {
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

    private fun resolvePermissionField(playerClass: Class<*>): Field =
        fieldCache.computeIfAbsent(playerClass, ::findPermissionField)

    private fun findPermissionField(playerClass: Class<*>): Field {
        var currentClass: Class<*>? = playerClass

        while (currentClass != null) {
            val declaredFields = currentClass.declaredFields

            for (fieldName in candidateFieldNames) {
                declaredFields
                    .firstOrNull { field ->
                        field.name == fieldName && PermissibleBase::class.java.isAssignableFrom(field.type)
                    }?.let { field ->
                        field.isAccessible = true
                        return field
                    }
            }

            declaredFields
                .firstOrNull { field -> PermissibleBase::class.java.isAssignableFrom(field.type) }
                ?.let { field ->
                    field.isAccessible = true
                    return field
                }

            currentClass = currentClass.superclass
        }

        throw NoSuchFieldException("Could not resolve a permissible field on ${playerClass.name}")
    }
}
