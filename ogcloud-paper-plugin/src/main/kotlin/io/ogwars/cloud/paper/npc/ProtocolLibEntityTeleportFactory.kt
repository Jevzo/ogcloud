package io.ogwars.cloud.paper.npc

import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.reflect.EquivalentConverter
import com.comphenix.protocol.utility.MinecraftReflection
import com.comphenix.protocol.wrappers.BukkitConverters
import com.comphenix.protocol.wrappers.EnumWrappers
import org.bukkit.Location
import org.bukkit.util.Vector
import java.lang.reflect.Constructor

internal object ProtocolLibEntityTeleportFactory {
    private val modernFactory = ModernEntityTeleportFactory.createOrNull()

    fun writeTeleport(
        packet: PacketContainer,
        entityId: Int,
        location: Location,
        onGround: Boolean,
    ) {
        if (packet.doubles.size() >= 3 && packet.bytes.size() >= 2) {
            packet.integers.write(0, entityId)
            packet.doubles
                .write(0, location.x)
                .write(1, location.y)
                .write(2, location.z)
            packet.bytes
                .write(0, toProtocolAngle(location.yaw))
                .write(1, toProtocolAngle(location.pitch))
            packet.booleans.write(0, onGround)
            return
        }

        val factory =
            modernFactory ?: throw IllegalStateException("No compatible ProtocolLib entity teleport factory available")
        factory.write(packet, entityId, location, onGround)
    }

    private fun toProtocolAngle(angle: Float): Byte = (angle * 256.0f / 360.0f).toInt().toByte()

    private class ModernEntityTeleportFactory(
        private val positionMoveRotationClass: Class<*>,
        private val positionMoveRotationConstructor: Constructor<*>,
        private val relativeConverter: EquivalentConverter<RelativeFlag>,
    ) {
        private val vec3Converter = BukkitConverters.getVectorConverter()

        @Suppress("UNCHECKED_CAST")
        private val positionMoveRotationType = positionMoveRotationClass as Class<Any>

        fun write(
            packet: PacketContainer,
            entityId: Int,
            location: Location,
            onGround: Boolean,
        ) {
            packet.integers.write(0, entityId)
            packet.getSpecificModifier(positionMoveRotationType).write(0, createPositionMoveRotation(location))
            packet.getSets(relativeConverter).write(0, emptySet<RelativeFlag>())
            packet.booleans.write(0, onGround)
        }

        private fun createPositionMoveRotation(location: Location): Any {
            val position = vec3Converter.getGeneric(location.toVector())
            val deltaMovement = vec3Converter.getGeneric(Vector())
            return positionMoveRotationConstructor.newInstance(
                position,
                deltaMovement,
                location.yaw,
                location.pitch,
            )
        }

        companion object {
            fun createOrNull(): ModernEntityTeleportFactory? =
                runCatching {
                    val positionMoveRotationClass =
                        runCatching { Class.forName("net.minecraft.world.entity.PositionMoveRotation") }
                            .getOrElse {
                                MinecraftReflection.getMinecraftClass("world.entity.PositionMoveRotation")
                            }

                    val relativeClass =
                        runCatching { Class.forName("net.minecraft.world.entity.Relative") }
                            .getOrElse {
                                MinecraftReflection.getMinecraftClass("world.entity.Relative")
                            }

                    val positionMoveRotationConstructor =
                        positionMoveRotationClass.getConstructor(
                            MinecraftReflection.getVec3DClass(),
                            MinecraftReflection.getVec3DClass(),
                            Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                        )

                    val relativeConverter =
                        EnumWrappers.getGenericConverter(relativeClass, RelativeFlag::class.java)

                    ModernEntityTeleportFactory(
                        positionMoveRotationClass = positionMoveRotationClass,
                        positionMoveRotationConstructor = positionMoveRotationConstructor,
                        relativeConverter = relativeConverter,
                    )
                }.getOrNull()
        }
    }

    private enum class RelativeFlag {
        X,
        Y,
        Z,
        Y_ROT,
        X_ROT,
        DELTA_X,
        DELTA_Y,
        DELTA_Z,
        ROTATE_DELTA,
    }
}
