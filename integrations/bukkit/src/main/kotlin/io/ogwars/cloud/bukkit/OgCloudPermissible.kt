package io.ogwars.cloud.bukkit

import io.ogwars.cloud.node.core.permission.Decision
import org.bukkit.entity.Player
import org.bukkit.permissions.Permissible
import org.bukkit.permissions.PermissibleBase
import org.bukkit.permissions.Permission
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/** As v1: a player holds OgCloud's nodes on top of everything Bukkit grants them anyway, such as op defaults. */
class OgCloudPermissible(
    private val player: Player,
    private val decide: (Player, String) -> Decision,
) : PermissibleBase(player) {
    override fun hasPermission(name: String): Boolean =
        decide(player, name) == Decision.ALLOW || super.hasPermission(name)

    override fun hasPermission(permission: Permission): Boolean =
        decide(player, permission.name) == Decision.ALLOW || super.hasPermission(permission)
}

/**
 * Puts an [OgCloudPermissible] into a player. Bukkit has no API for that, so the player's [PermissibleBase] field is
 * replaced, found by type so it works across server versions (v1's approach).
 */
object PermissionInjector {
    private val fields = ConcurrentHashMap<Class<*>, Field>()

    fun inject(
        player: Player,
        decide: (Player, String) -> Decision,
    ) {
        val field = fields.computeIfAbsent(player.javaClass, ::permissibleField)
        if (field.get(player) !is OgCloudPermissible) field.set(player, OgCloudPermissible(player, decide))
        player.recalculatePermissions()
    }

    private fun permissibleField(type: Class<*>): Field =
        generateSequence(type) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull {
                Permissible::class.java.isAssignableFrom(it.type) &&
                    PermissibleBase::class.java.isAssignableFrom(it.type)
            }?.apply { isAccessible = true }
            ?: throw IllegalStateException("${type.name} has no PermissibleBase field to replace")
}
