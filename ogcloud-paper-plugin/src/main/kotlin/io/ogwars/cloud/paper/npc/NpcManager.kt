package io.ogwars.cloud.paper.npc

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.manager.player.PlayerManager
import com.github.retrooper.packetevents.protocol.player.GameMode
import com.github.retrooper.packetevents.protocol.player.SkinSection
import com.github.retrooper.packetevents.protocol.player.TextureProperty
import com.github.retrooper.packetevents.protocol.player.UserProfile
import com.github.retrooper.packetevents.protocol.world.Location as PacketLocation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.google.gson.Gson
import io.ogwars.cloud.common.event.PlayerTransferEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.common.model.NpcClickActionType
import io.ogwars.cloud.common.model.NpcDefinition
import io.ogwars.cloud.common.model.NpcLookAtConfig
import io.ogwars.cloud.common.model.NpcModel
import io.ogwars.cloud.common.model.NpcSkin
import io.ogwars.cloud.common.model.NpcTransferStrategy
import io.ogwars.cloud.paper.kafka.KafkaSendDispatcher
import io.ogwars.cloud.server.api.OgCloudNpcClickType
import io.ogwars.cloud.server.api.OgCloudNpcInteraction
import io.ogwars.cloud.server.api.OgCloudRuntimeNpcBuilder
import io.ogwars.cloud.server.api.OgCloudRuntimeNpcHandle
import io.ogwars.cloud.server.api.OgCloudSubscription
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.logging.Logger
import kotlin.math.atan2
import kotlin.math.sqrt

class NpcManager(
    private val plugin: JavaPlugin,
    private val kafkaSendDispatcher: KafkaSendDispatcher,
    private val logger: Logger,
) {
    private val gson = Gson()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val playerManager: PlayerManager = PacketEvents.getAPI().playerManager
    private val packetListener = NpcPacketListener(plugin, this)
    private val entries = LinkedHashMap<String, NpcEntry>()
    private val entityIds = ConcurrentHashMap<Int, String>()
    private val nextEntityId = AtomicInteger(FIRST_ENTITY_ID)
    private var syncTask: BukkitTask? = null

    fun start() {
        PacketEvents.getAPI().eventManager.registerListener(packetListener)
        syncTask =
            plugin.server.scheduler.runTaskTimer(
                plugin,
                Runnable { tick() },
                SYNC_INTERVAL_TICKS,
                SYNC_INTERVAL_TICKS,
            )
    }

    fun shutdown() {
        syncTask?.cancel()
        syncTask = null
        PacketEvents.getAPI().eventManager.unregisterListener(packetListener)

        entries.values.toList().forEach(::removeEntry)
        entries.clear()
        entityIds.clear()
    }

    fun runtimeNpc(id: String): OgCloudRuntimeNpcBuilder = RuntimeNpcBuilder(this, id)

    fun findRuntimeNpc(id: String): OgCloudRuntimeNpcHandle? {
        requireMainThread()
        return entries[id]?.takeIf { !it.managed }?.let { RuntimeNpcHandle(this, id) }
    }

    fun upsertManagedNpc(definition: NpcDefinition) {
        requireMainThread()

        val existing = entries[definition.id]
        if (existing == null) {
            val entry = createEntry(definition, managed = true)
            entries[entry.id] = entry
            refreshArmorStands(entry)
            return
        }

        if (!existing.managed) {
            logger.warning("Ignoring managed npc upsert because runtime npc id already exists: ${definition.id}")
            return
        }

        applyDefinition(existing, definition)
    }

    fun removeManagedNpc(id: String) {
        requireMainThread()
        entries[id]?.takeIf { it.managed }?.let(::removeEntry)
    }

    fun getLocalNpc(id: String): NpcDefinition? {
        requireMainThread()
        return entries[id]?.definition
    }

    fun getLocalNpcs(): List<NpcDefinition> {
        requireMainThread()
        return entries.values.map(NpcEntry::definition)
    }

    fun findLookTarget(player: Player): NpcDefinition? {
        requireMainThread()
        return resolveLookTargetCandidate(player)
    }

    fun handleViewerQuit(playerUuid: UUID) {
        requireMainThread()
        entries.values.forEach { it.viewers.remove(playerUuid) }
    }

    internal fun findNpcIdByEntityId(entityId: Int): String? = entityIds[entityId]

    internal fun handleInteraction(
        npcId: String,
        player: Player,
        clickType: OgCloudNpcClickType,
    ) {
        requireMainThread()
        val entry = entries[npcId] ?: return
        dispatchInteraction(entry, OgCloudNpcInteraction(entry.id, player, clickType))
    }

    internal fun spawnRuntimeNpc(config: RuntimeNpcConfig): OgCloudRuntimeNpcHandle {
        requireMainThread()

        if (entries.containsKey(config.id)) {
            throw IllegalArgumentException("NPC already exists: ${config.id}")
        }

        val definition =
            NpcDefinition(
                id = config.id,
                group = LOCAL_RUNTIME_GROUP,
                location = npcLocationFromBukkit(config.location),
                title = config.title,
                subtitle = config.subtitle,
                model = config.model,
                skin = config.skin,
                lookAt = config.lookAt,
            )
        val entry = createEntry(definition, managed = false)
        entry.leftSubscribers.putAll(config.leftSubscribers)
        entry.rightSubscribers.putAll(config.rightSubscribers)

        entries[entry.id] = entry
        refreshArmorStands(entry)

        return RuntimeNpcHandle(this, entry.id)
    }

    internal fun getRuntimeLocation(id: String): Location {
        requireMainThread()
        return requireRuntimeEntry(id).toBukkitLocation()
            ?: throw IllegalStateException("World not loaded for npc $id")
    }

    internal fun teleportRuntimeNpc(
        id: String,
        location: Location,
    ) {
        requireMainThread()
        val entry = requireRuntimeEntry(id)
        applyDefinition(entry, entry.definition.copy(location = npcLocationFromBukkit(location)))
    }

    internal fun setRuntimeTitle(
        id: String,
        title: String?,
    ) {
        requireMainThread()
        val entry = requireRuntimeEntry(id)
        applyDefinition(entry, entry.definition.copy(title = title))
    }

    internal fun setRuntimeSubtitle(
        id: String,
        subtitle: String?,
    ) {
        requireMainThread()
        val entry = requireRuntimeEntry(id)
        applyDefinition(entry, entry.definition.copy(subtitle = subtitle))
    }

    internal fun setRuntimeModel(
        id: String,
        model: NpcModel,
    ) {
        requireMainThread()
        val entry = requireRuntimeEntry(id)
        applyDefinition(entry, entry.definition.copy(model = model))
    }

    internal fun setRuntimeSkin(
        id: String,
        skin: NpcSkin?,
    ) {
        requireMainThread()
        val entry = requireRuntimeEntry(id)
        applyDefinition(entry, entry.definition.copy(skin = skin))
    }

    internal fun setRuntimeLookAt(
        id: String,
        enabled: Boolean,
        radius: Double,
    ) {
        requireMainThread()
        val entry = requireRuntimeEntry(id)
        applyDefinition(entry, entry.definition.copy(lookAt = NpcLookAtConfig(enabled, radius)))
    }

    internal fun subscribeRuntimeLeftClick(
        id: String,
        listener: Consumer<OgCloudNpcInteraction>,
    ): OgCloudSubscription {
        requireMainThread()
        val entry = requireRuntimeEntry(id)
        val token = UUID.randomUUID()
        entry.leftSubscribers[token] = listener
        return OgCloudSubscription { entry.leftSubscribers.remove(token) }
    }

    internal fun subscribeRuntimeRightClick(
        id: String,
        listener: Consumer<OgCloudNpcInteraction>,
    ): OgCloudSubscription {
        requireMainThread()
        val entry = requireRuntimeEntry(id)
        val token = UUID.randomUUID()
        entry.rightSubscribers[token] = listener
        return OgCloudSubscription { entry.rightSubscribers.remove(token) }
    }

    internal fun despawnRuntimeNpc(id: String) {
        requireMainThread()
        removeEntry(requireRuntimeEntry(id))
    }

    private fun tick() {
        entries.values.forEach { entry ->
            syncViewers(entry)
            updateLookAt(entry)
        }
    }

    private fun resolveLookTargetCandidate(player: Player): NpcDefinition? {
        val eyeLocation = player.eyeLocation
        val eyeVector = eyeLocation.toVector()
        val direction = eyeLocation.direction.normalize()

        return entries.values
            .asSequence()
            .filter { entry -> entry.worldName == player.world.name }
            .mapNotNull { entry ->
                val npcLocation = entry.toBukkitLocation() ?: return@mapNotNull null
                val npcCenter = npcLocation.clone().add(0.0, NPC_EYE_HEIGHT, 0.0).toVector()
                val offset = npcCenter.subtract(eyeVector)
                val along = offset.dot(direction)

                if (along < 0.0 || along > LOOK_TARGET_MAX_DISTANCE) {
                    return@mapNotNull null
                }

                val perpendicularSquared = offset.lengthSquared() - (along * along)
                if (perpendicularSquared > LOOK_TARGET_RADIUS_SQUARED) {
                    return@mapNotNull null
                }

                along to entry.definition
            }.minByOrNull { it.first }
            ?.second
    }

    private fun dispatchInteraction(
        entry: NpcEntry,
        interaction: OgCloudNpcInteraction,
    ) {
        if (entry.managed) {
            val action =
                when (interaction.clickType) {
                    OgCloudNpcClickType.LEFT -> entry.definition.leftAction
                    OgCloudNpcClickType.RIGHT -> entry.definition.rightAction
                }
            val targetGroup = action.targetGroup

            if (action.type == NpcClickActionType.TRANSFER && !targetGroup.isNullOrBlank()) {
                publishTransfer(interaction.player, entry.id, targetGroup, action.routingStrategy)
            }

            return
        }

        val listeners =
            when (interaction.clickType) {
                OgCloudNpcClickType.LEFT -> entry.leftSubscribers.values.toList()
                OgCloudNpcClickType.RIGHT -> entry.rightSubscribers.values.toList()
            }

        listeners.forEach { listener ->
            runCatching { listener.accept(interaction) }
                .onFailure { logger.warning("NPC subscriber failed for ${entry.id}: ${it.message}") }
        }
    }

    private fun applyDefinition(
        entry: NpcEntry,
        definition: NpcDefinition,
    ) {
        val previous = entry.definition
        val requiresRespawn =
            previous.skin != definition.skin ||
                previous.model != definition.model ||
                previous.location.world != definition.location.world

        entry.definition = definition
        entry.currentRotation = Rotation(definition.location.yaw, definition.location.pitch)

        if (previous.model != definition.model) {
            entry.profileUuid = createProfileUuid(entry.id, definition.model)
        }

        refreshArmorStands(entry)

        if (requiresRespawn) {
            respawnEntry(entry)
            return
        }

        syncViewers(entry)

        val viewers = resolveViewers(entry)
        viewers.forEach { player ->
            sendTeleportPacket(entry, player)
            sendRotationPackets(entry, player, entry.currentRotation)
        }
    }

    private fun createEntry(
        definition: NpcDefinition,
        managed: Boolean,
    ): NpcEntry {
        val entry =
            NpcEntry(
                id = definition.id,
                managed = managed,
                entityId = nextEntityId.getAndIncrement(),
                profileName = createProfileName(definition.id),
                teamName = createTeamName(definition.id),
                profileUuid = createProfileUuid(definition.id, definition.model),
                definition = definition,
                currentRotation = Rotation(definition.location.yaw, definition.location.pitch),
            )
        entityIds[entry.entityId] = entry.id
        return entry
    }

    private fun syncViewers(entry: NpcEntry) {
        val npcLocation = entry.toBukkitLocation()
        if (npcLocation == null) {
            resolveViewers(entry).forEach { player -> despawnForViewer(entry, player) }
            return
        }
        val desiredViewers =
            plugin.server.onlinePlayers
                .filter { shouldView(entry, npcLocation, it) }
                .associateBy(Player::getUniqueId)

        entry.viewers.toList()
            .filterNot(desiredViewers::containsKey)
            .forEach { viewerUuid ->
                plugin.server.getPlayer(viewerUuid)?.let { player -> despawnForViewer(entry, player) } ?: entry.viewers.remove(viewerUuid)
            }

        desiredViewers.values
            .filterNot { it.uniqueId in entry.viewers }
            .forEach { player -> spawnForViewer(entry, player) }
    }

    private fun shouldView(
        entry: NpcEntry,
        npcLocation: Location,
        player: Player,
    ): Boolean {
        if (!player.isOnline || player.world.name != entry.worldName) {
            return false
        }

        return player.location.distanceSquared(npcLocation) <= VIEW_DISTANCE_SQUARED
    }

    private fun updateLookAt(entry: NpcEntry) {
        val npcLocation = entry.toBukkitLocation() ?: return
        val desiredRotation =
            if (!entry.definition.lookAt.enabled) {
                Rotation(entry.definition.location.yaw, entry.definition.location.pitch)
            } else {
                resolveLookTarget(entry, npcLocation)?.let { target ->
                    computeRotation(npcLocation, target.eyeLocation)
                } ?: Rotation(entry.definition.location.yaw, entry.definition.location.pitch)
            }

        if (desiredRotation.isCloseTo(entry.currentRotation)) {
            return
        }

        entry.currentRotation = desiredRotation
        resolveViewers(entry).forEach { player ->
            sendRotationPackets(entry, player, desiredRotation)
        }
    }

    private fun resolveLookTarget(
        entry: NpcEntry,
        npcLocation: Location,
    ): Player? =
        plugin.server.onlinePlayers
            .asSequence()
            .filter { player -> player.world.name == entry.worldName }
            .filter { player -> player.location.distanceSquared(npcLocation) <= entry.definition.lookAt.radius * entry.definition.lookAt.radius }
            .minByOrNull { player -> player.location.distanceSquared(npcLocation) }

    private fun refreshArmorStands(entry: NpcEntry) {
        val world = Bukkit.getWorld(entry.worldName)
        if (world == null) {
            removeArmorStand(entry.titleStand)
            removeArmorStand(entry.subtitleStand)
            entry.titleStand = null
            entry.subtitleStand = null
            return
        }

        entry.titleStand =
            refreshArmorStand(
                current = entry.titleStand,
                rawText = entry.definition.title,
                location = armorStandLocation(world, entry.definition, titleLine = true),
            )
        entry.subtitleStand =
            refreshArmorStand(
                current = entry.subtitleStand,
                rawText = entry.definition.subtitle,
                location = armorStandLocation(world, entry.definition, titleLine = false),
            )
    }

    private fun removeEntry(entry: NpcEntry) {
        resolveViewers(entry).forEach { player -> despawnForViewer(entry, player) }
        removeArmorStand(entry.titleStand)
        removeArmorStand(entry.subtitleStand)
        entries.remove(entry.id)
        entityIds.remove(entry.entityId)
    }

    private fun requireRuntimeEntry(id: String): NpcEntry {
        val entry = entries[id] ?: throw IllegalArgumentException("NPC not found: $id")
        if (entry.managed) {
            throw IllegalArgumentException("NPC is managed by OgCloud and cannot be mutated through the runtime handle: $id")
        }
        return entry
    }

    private fun requireMainThread() {
        check(Bukkit.isPrimaryThread()) { "NPC operations must be performed on the main thread" }
    }

    private fun respawnEntry(entry: NpcEntry) {
        val viewers = resolveViewers(entry)
        viewers.forEach { player -> despawnForViewer(entry, player) }
        syncViewers(entry)
    }

    private fun resolveViewers(entry: NpcEntry): List<Player> =
        entry.viewers.mapNotNull(plugin.server::getPlayer)

    private fun spawnForViewer(
        entry: NpcEntry,
        player: Player,
    ) {
        val location = entry.toBukkitLocation() ?: return
        val playerInfo = createPlayerInfo(entry)

        sendPacket(
            player,
            WrapperPlayServerTeams(
                entry.teamName,
                WrapperPlayServerTeams.TeamMode.CREATE,
                TEAM_INFO,
                listOf(entry.profileName),
            ),
        )
        sendPacket(player, WrapperPlayServerPlayerInfoUpdate(PLAYER_INFO_ACTIONS, playerInfo))
        sendPacket(
            player,
            WrapperPlayServerSpawnPlayer(
                entry.entityId,
                entry.profileUuid,
                location.toPacketLocation(),
                emptyList(),
            ),
        )
        sendPacket(player, WrapperPlayServerEntityMetadata(entry.entityId, playerMetadata()))
        sendRotationPackets(entry, player, entry.currentRotation)

        entry.viewers.add(player.uniqueId)

        plugin.server.scheduler.runTaskLater(
            plugin,
            Runnable {
                if (player.isOnline && player.uniqueId in entry.viewers) {
                    sendPacket(player, WrapperPlayServerPlayerInfoRemove(listOf(entry.profileUuid)))
                }
            },
            TABLIST_REMOVE_DELAY_TICKS,
        )
    }

    private fun despawnForViewer(
        entry: NpcEntry,
        player: Player,
    ) {
        sendPacket(player, WrapperPlayServerDestroyEntities(entry.entityId))
        sendPacket(player, WrapperPlayServerPlayerInfoRemove(listOf(entry.profileUuid)))
        sendPacket(
            player,
            WrapperPlayServerTeams(
                entry.teamName,
                WrapperPlayServerTeams.TeamMode.REMOVE,
                null as WrapperPlayServerTeams.ScoreBoardTeamInfo?,
                emptyList<String>(),
            ),
        )
        entry.viewers.remove(player.uniqueId)
    }

    private fun sendTeleportPacket(
        entry: NpcEntry,
        player: Player,
    ) {
        val location = entry.toBukkitLocation() ?: return
        sendPacket(player, WrapperPlayServerEntityTeleport(entry.entityId, location.toPacketLocation(), false))
    }

    private fun sendRotationPackets(
        entry: NpcEntry,
        player: Player,
        rotation: Rotation,
    ) {
        sendPacket(player, WrapperPlayServerEntityRotation(entry.entityId, rotation.yaw, rotation.pitch, false))
        sendPacket(player, WrapperPlayServerEntityHeadLook(entry.entityId, rotation.yaw))
    }

    private fun sendPacket(
        player: Player,
        packet: Any,
    ) {
        playerManager.sendPacket(player, packet)
    }

    private fun publishTransfer(
        player: Player,
        npcId: String,
        targetGroup: String,
        routingStrategy: NpcTransferStrategy?,
    ) {
        val payload =
            gson.toJson(
                PlayerTransferEvent(
                    playerUuid = player.uniqueId.toString(),
                    target = targetGroup,
                    routingStrategy = routingStrategy,
                    reason = "npc-click:$npcId",
                ),
            )

        kafkaSendDispatcher.dispatch(
            KafkaSendDispatcher.Message(
                topic = KafkaTopics.PLAYER_TRANSFER,
                key = player.uniqueId.toString(),
                payload = payload,
                type = KafkaSendDispatcher.MessageType.PLAYER_TRANSFER,
            ),
        )
    }

    private fun createPlayerInfo(entry: NpcEntry): WrapperPlayServerPlayerInfoUpdate.PlayerInfo {
        val textures =
            entry.definition.skin
                ?.let { skin -> mutableListOf(TextureProperty(TEXTURE_PROPERTY_NAME, skin.textureValue, skin.textureSignature)) }
                ?: mutableListOf()
        val profile = UserProfile(entry.profileUuid, entry.profileName, textures)

        return WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile).apply {
            setGameMode(GameMode.SURVIVAL)
            setLatency(0)
            setListed(true)
            setDisplayName(Component.empty())
            setShowHat(true)
        }
    }

    private fun playerMetadata(): List<EntityData<*>> =
        listOf(
            EntityData(PLAYER_SKIN_PARTS_INDEX, EntityDataTypes.BYTE, SkinSection.ALL.mask),
        )

    private fun refreshArmorStand(
        current: ArmorStand?,
        rawText: String?,
        location: Location,
    ): ArmorStand? {
        if (rawText.isNullOrBlank()) {
            removeArmorStand(current)
            return null
        }

        val stand =
            current
                ?.takeIf { it.isValid && it.world.uid == location.world.uid }
                ?: location.world.spawn(location, ArmorStand::class.java).apply(::configureArmorStand)

        stand.teleport(location)
        stand.customName(legacySerializer.deserialize(rawText))

        return stand
    }

    private fun configureArmorStand(stand: ArmorStand) {
        stand.isVisible = false
        stand.isPersistent = false
        stand.isInvulnerable = true
        stand.isSmall = true
        stand.isMarker = true
        stand.isCustomNameVisible = true
        stand.isSilent = true
        stand.setGravity(false)
        stand.setCollidable(false)
    }

    private fun removeArmorStand(stand: ArmorStand?) {
        stand?.remove()
    }

    private fun armorStandLocation(
        world: World,
        definition: NpcDefinition,
        titleLine: Boolean,
    ): Location {
        val yOffset =
            when {
                titleLine && !definition.subtitle.isNullOrBlank() -> TITLE_Y_OFFSET
                !titleLine -> SUBTITLE_Y_OFFSET
                else -> SINGLE_LINE_Y_OFFSET
            }

        val location = definition.location
        return Location(world, location.x, location.y + yOffset, location.z, location.yaw, location.pitch)
    }

    private fun computeRotation(
        from: Location,
        to: Location,
    ): Rotation {
        val xDiff = to.x - from.x
        val zDiff = to.z - from.z
        val yDiff = to.y - (from.y + NPC_EYE_HEIGHT)
        val horizontalDistance = sqrt((xDiff * xDiff) + (zDiff * zDiff))

        val yaw = Math.toDegrees(atan2(-xDiff, zDiff)).toFloat()
        val pitch = Math.toDegrees(-atan2(yDiff, horizontalDistance)).toFloat()
        return Rotation(yaw, pitch)
    }

    private fun Location.toPacketLocation(): PacketLocation =
        PacketLocation(x, y, z, yaw, pitch)

    private fun createProfileName(id: String): String = "npc${stableSuffix(id).take(13)}"

    private fun createTeamName(id: String): String = "ognpc_${stableSuffix(id)}"

    private fun stableSuffix(id: String): String =
        UUID
            .nameUUIDFromBytes("ogcloud-npc:$id".toByteArray(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "")

    private fun createProfileUuid(
        id: String,
        model: NpcModel,
    ): UUID {
        val base = UUID.nameUUIDFromBytes("ogcloud-npc-profile:$id".toByteArray(StandardCharsets.UTF_8))
        val wantsAlex = model == NpcModel.ALEX
        val isAlex = (base.hashCode() and 1) == 1

        return if (wantsAlex == isAlex) {
            base
        } else {
            UUID(base.mostSignificantBits, base.leastSignificantBits xor 1L)
        }
    }

    private class RuntimeNpcBuilder(
        private val manager: NpcManager,
        private val id: String,
    ) : OgCloudRuntimeNpcBuilder {
        private var location: Location? = null
        private var title: String? = null
        private var subtitle: String? = null
        private var model: NpcModel = NpcModel.STEVE
        private var skin: NpcSkin? = null
        private var lookAt = NpcLookAtConfig()
        private val leftSubscribers = linkedMapOf<UUID, Consumer<OgCloudNpcInteraction>>()
        private val rightSubscribers = linkedMapOf<UUID, Consumer<OgCloudNpcInteraction>>()

        override fun location(location: Location): OgCloudRuntimeNpcBuilder {
            this.location = location.clone()
            return this
        }

        override fun title(title: String?): OgCloudRuntimeNpcBuilder {
            this.title = title
            return this
        }

        override fun subtitle(subtitle: String?): OgCloudRuntimeNpcBuilder {
            this.subtitle = subtitle
            return this
        }

        override fun model(model: NpcModel): OgCloudRuntimeNpcBuilder {
            this.model = model
            return this
        }

        override fun skin(
            textureValue: String,
            textureSignature: String?,
        ): OgCloudRuntimeNpcBuilder {
            this.skin = NpcSkin(textureValue, textureSignature)
            return this
        }

        override fun clearSkin(): OgCloudRuntimeNpcBuilder {
            this.skin = null
            return this
        }

        override fun lookAt(
            enabled: Boolean,
            radius: Double,
        ): OgCloudRuntimeNpcBuilder {
            this.lookAt = NpcLookAtConfig(enabled, radius)
            return this
        }

        override fun onLeftClick(listener: Consumer<OgCloudNpcInteraction>): OgCloudRuntimeNpcBuilder {
            leftSubscribers[UUID.randomUUID()] = listener
            return this
        }

        override fun onRightClick(listener: Consumer<OgCloudNpcInteraction>): OgCloudRuntimeNpcBuilder {
            rightSubscribers[UUID.randomUUID()] = listener
            return this
        }

        override fun spawn(): OgCloudRuntimeNpcHandle =
            manager.spawnRuntimeNpc(
                RuntimeNpcConfig(
                    id = id,
                    location = location ?: throw IllegalArgumentException("NPC location is required"),
                    title = title,
                    subtitle = subtitle,
                    model = model,
                    skin = skin,
                    lookAt = lookAt,
                    leftSubscribers = leftSubscribers.toMap(),
                    rightSubscribers = rightSubscribers.toMap(),
                ),
            )
    }

    private class RuntimeNpcHandle(
        private val manager: NpcManager,
        private val id: String,
    ) : OgCloudRuntimeNpcHandle {
        override fun getId(): String = id

        override fun getLocation(): Location = manager.getRuntimeLocation(id)

        override fun teleport(location: Location) {
            manager.teleportRuntimeNpc(id, location)
        }

        override fun setTitle(title: String?) {
            manager.setRuntimeTitle(id, title)
        }

        override fun setSubtitle(subtitle: String?) {
            manager.setRuntimeSubtitle(id, subtitle)
        }

        override fun setModel(model: NpcModel) {
            manager.setRuntimeModel(id, model)
        }

        override fun skin(
            textureValue: String,
            textureSignature: String?,
        ) {
            manager.setRuntimeSkin(id, NpcSkin(textureValue, textureSignature))
        }

        override fun clearSkin() {
            manager.setRuntimeSkin(id, null)
        }

        override fun setLookAt(
            enabled: Boolean,
            radius: Double,
        ) {
            manager.setRuntimeLookAt(id, enabled, radius)
        }

        override fun subscribeLeftClick(listener: Consumer<OgCloudNpcInteraction>): OgCloudSubscription =
            manager.subscribeRuntimeLeftClick(id, listener)

        override fun subscribeRightClick(listener: Consumer<OgCloudNpcInteraction>): OgCloudSubscription =
            manager.subscribeRuntimeRightClick(id, listener)

        override fun despawn() {
            manager.despawnRuntimeNpc(id)
        }
    }

    companion object {
        private const val FIRST_ENTITY_ID = 2_000_000
        private const val SYNC_INTERVAL_TICKS = 10L
        private const val LOCAL_RUNTIME_GROUP = "__runtime__"
        private const val VIEW_DISTANCE_SQUARED = 96.0 * 96.0
        private const val LOOK_TARGET_MAX_DISTANCE = 8.0
        private const val LOOK_TARGET_RADIUS_SQUARED = 1.75 * 1.75
        private const val NPC_EYE_HEIGHT = 1.62
        private const val PLAYER_SKIN_PARTS_INDEX = 17
        private const val TABLIST_REMOVE_DELAY_TICKS = 20L
        private const val TITLE_Y_OFFSET = 2.35
        private const val SUBTITLE_Y_OFFSET = 2.10
        private const val SINGLE_LINE_Y_OFFSET = 2.20
        private const val TEXTURE_PROPERTY_NAME = "textures"
        private val TEAM_INFO =
            WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.NEVER,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                NamedTextColor.WHITE,
                WrapperPlayServerTeams.OptionData.NONE,
            )
        private val PLAYER_INFO_ACTIONS =
            EnumSet.of(
                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_HAT,
            )
    }
}
