package io.ogwars.cloud.paper.npc

import io.ogwars.cloud.common.model.NpcSkin
import com.comphenix.protocol.wrappers.WrappedGameProfile
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.UUID

internal object ProtocolLibGameProfileFactory {
    private val authlibFactory = AuthlibGameProfileFactory.createOrNull()

    fun createProfile(
        uuid: UUID,
        name: String,
        skin: NpcSkin?,
    ): WrappedGameProfile {
        authlibFactory?.let { factory ->
            return factory.createProfile(uuid, name, skin)
        }

        // No authlib reflection path available. Avoid ProtocolLib property mutation entirely.
        return WrappedGameProfile(uuid, name)
    }

    private class AuthlibGameProfileFactory(
        private val constructProfileWithPropertyMap: MethodHandle?,
        private val constructProfileSimple: MethodHandle?,
        private val constructPropertyMapEmpty: MethodHandle?,
        private val constructPropertyMapFromMultimap: MethodHandle?,
        private val constructProperty: MethodHandle?,
        private val getProperties: MethodHandle?,
        private val putProperty: MethodHandle?,
        private val createMultimap: MethodHandle?,
        private val putMultimapEntry: MethodHandle?,
    ) {
        fun createProfile(
            uuid: UUID,
            name: String,
            skin: NpcSkin?,
        ): WrappedGameProfile {
            val handle =
                when {
                    constructProfileWithPropertyMap != null &&
                        constructPropertyMapEmpty != null &&
                        constructProperty != null &&
                        putProperty != null &&
                        skin != null -> {
                        val propertyMap = constructPropertyMapEmpty.invoke()
                        val property =
                            constructProperty.invoke(
                                TEXTURE_PROPERTY_NAME,
                                skin.textureValue,
                                skin.textureSignature,
                            )
                        putProperty.invoke(propertyMap, TEXTURE_PROPERTY_NAME, property)
                        constructProfileWithPropertyMap.invoke(uuid, name, propertyMap)
                    }

                    constructProfileWithPropertyMap != null &&
                        constructPropertyMapFromMultimap != null &&
                        constructProperty != null &&
                        createMultimap != null &&
                        putMultimapEntry != null &&
                        skin != null -> {
                        val multimap = createMultimap.invoke()
                        val property =
                            constructProperty.invoke(
                                TEXTURE_PROPERTY_NAME,
                                skin.textureValue,
                                skin.textureSignature,
                            )
                        putMultimapEntry.invoke(multimap, TEXTURE_PROPERTY_NAME, property)
                        val propertyMap = constructPropertyMapFromMultimap.invoke(multimap)
                        constructProfileWithPropertyMap.invoke(uuid, name, propertyMap)
                    }

                    constructProfileSimple != null &&
                        skin != null &&
                        getProperties != null &&
                        constructProperty != null &&
                        putProperty != null -> {
                        val profile = constructProfileSimple.invoke(uuid, name)
                        val propertyMap = getProperties.invoke(profile)
                        val property =
                            constructProperty.invoke(
                                TEXTURE_PROPERTY_NAME,
                                skin.textureValue,
                                skin.textureSignature,
                            )
                        putProperty.invoke(propertyMap, TEXTURE_PROPERTY_NAME, property)
                        profile
                    }

                    constructProfileSimple != null -> constructProfileSimple.invoke(uuid, name)
                    else -> throw IllegalStateException("No supported authlib GameProfile constructor found")
                }

            return WrappedGameProfile.fromHandle(handle)
        }

        companion object {
            fun createOrNull(): AuthlibGameProfileFactory? =
                runCatching {
                    val lookup = MethodHandles.publicLookup()
                    val gameProfileClass = Class.forName("com.mojang.authlib.GameProfile")
                    val propertyClass = Class.forName("com.mojang.authlib.properties.Property")
                    val propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap")

                    val constructProfileWithPropertyMap =
                        runCatching {
                            lookup
                                .findConstructor(
                                    gameProfileClass,
                                    MethodType.methodType(
                                        Void.TYPE,
                                        UUID::class.java,
                                        String::class.java,
                                        propertyMapClass,
                                    ),
                                ).asType(
                                    MethodType.methodType(
                                        Any::class.java,
                                        UUID::class.java,
                                        String::class.java,
                                        Any::class.java,
                                    ),
                                )
                        }.getOrNull()

                    val constructProfileSimple =
                        runCatching {
                            lookup
                                .findConstructor(
                                    gameProfileClass,
                                    MethodType.methodType(Void.TYPE, UUID::class.java, String::class.java),
                                ).asType(MethodType.methodType(Any::class.java, UUID::class.java, String::class.java))
                        }.getOrNull()

                    val constructProperty =
                        lookup
                            .findConstructor(
                                propertyClass,
                                MethodType.methodType(
                                    Void.TYPE,
                                    String::class.java,
                                    String::class.java,
                                    String::class.java,
                                ),
                            ).asType(
                                MethodType.methodType(
                                    Any::class.java,
                                    String::class.java,
                                    String::class.java,
                                    String::class.java,
                                ),
                            )

                    val constructPropertyMapEmpty =
                        runCatching {
                            lookup
                                .findConstructor(propertyMapClass, MethodType.methodType(Void.TYPE))
                                .asType(MethodType.methodType(Any::class.java))
                        }.getOrNull()

                    val constructPropertyMapFromMultimap =
                        runCatching {
                            val multimapClass = Class.forName("com.google.common.collect.Multimap")
                            lookup
                                .findConstructor(
                                    propertyMapClass,
                                    MethodType.methodType(Void.TYPE, multimapClass),
                                ).asType(MethodType.methodType(Any::class.java, Any::class.java))
                        }.getOrNull()

                    val getProperties =
                        runCatching {
                            lookup
                                .findVirtual(
                                    gameProfileClass,
                                    "getProperties",
                                    MethodType.methodType(propertyMapClass),
                                ).asType(MethodType.methodType(Any::class.java, Any::class.java))
                        }.getOrNull()

                    val putProperty =
                        runCatching {
                            lookup
                                .findVirtual(
                                    propertyMapClass,
                                    "put",
                                    MethodType.methodType(
                                        Boolean::class.javaPrimitiveType!!,
                                        Any::class.java,
                                        Any::class.java,
                                    ),
                                ).asType(
                                    MethodType.methodType(
                                        Boolean::class.javaPrimitiveType!!,
                                        Any::class.java,
                                        Any::class.java,
                                        Any::class.java,
                                    ),
                                )
                        }.getOrNull()

                    val createMultimap =
                        runCatching {
                            val hashMultimapClass = Class.forName("com.google.common.collect.HashMultimap")
                            lookup
                                .findStatic(hashMultimapClass, "create", MethodType.methodType(hashMultimapClass))
                                .asType(MethodType.methodType(Any::class.java))
                        }.getOrNull()

                    val putMultimapEntry =
                        runCatching {
                            val multimapClass = Class.forName("com.google.common.collect.Multimap")
                            lookup
                                .findVirtual(
                                    multimapClass,
                                    "put",
                                    MethodType.methodType(
                                        Boolean::class.javaPrimitiveType!!,
                                        Any::class.java,
                                        Any::class.java,
                                    ),
                                ).asType(
                                    MethodType.methodType(
                                        Boolean::class.javaPrimitiveType!!,
                                        Any::class.java,
                                        Any::class.java,
                                        Any::class.java,
                                    ),
                                )
                        }.getOrNull()

                    if (constructProfileWithPropertyMap == null && constructProfileSimple == null) {
                        return null
                    }

                    AuthlibGameProfileFactory(
                        constructProfileWithPropertyMap = constructProfileWithPropertyMap,
                        constructProfileSimple = constructProfileSimple,
                        constructPropertyMapEmpty = constructPropertyMapEmpty,
                        constructPropertyMapFromMultimap = constructPropertyMapFromMultimap,
                        constructProperty = constructProperty,
                        getProperties = getProperties,
                        putProperty = putProperty,
                        createMultimap = createMultimap,
                        putMultimapEntry = putMultimapEntry,
                    )
                }.getOrNull()
        }
    }

    private const val TEXTURE_PROPERTY_NAME = "textures"
}
