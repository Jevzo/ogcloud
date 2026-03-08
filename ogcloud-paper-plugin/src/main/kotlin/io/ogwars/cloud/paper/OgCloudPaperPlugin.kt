package io.ogwars.cloud.paper

import io.ogwars.cloud.paper.api.ApiClient
import io.ogwars.cloud.paper.api.OgCloudServerAPIImpl
import io.ogwars.cloud.paper.config.PaperPluginSettings
import io.ogwars.cloud.paper.gamestate.GameStateManager
import io.ogwars.cloud.paper.heartbeat.HeartbeatTask
import io.ogwars.cloud.paper.kafka.KafkaManager
import io.ogwars.cloud.paper.listener.ChatListener
import io.ogwars.cloud.paper.listener.CommandExecuteConsumer
import io.ogwars.cloud.paper.listener.LifecycleConsumer
import io.ogwars.cloud.paper.listener.NetworkUpdateConsumer
import io.ogwars.cloud.paper.listener.PermissionUpdateConsumer
import io.ogwars.cloud.paper.listener.PlayerJoinListener
import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.PermissionInjector
import io.ogwars.cloud.paper.permission.PermissionManager
import io.ogwars.cloud.paper.redis.RedisManager
import io.ogwars.cloud.paper.tablist.TablistTeamManager
import io.ogwars.cloud.server.api.OgCloudServerAPI
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin

class OgCloudPaperPlugin : JavaPlugin() {

    lateinit var kafkaManager: KafkaManager
        private set

    lateinit var heartbeatTask: HeartbeatTask
        private set

    lateinit var gameStateManager: GameStateManager
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var tablistTeamManager: TablistTeamManager
        private set

    lateinit var networkFeatureState: NetworkFeatureState
        private set

    private lateinit var redisManager: RedisManager
    private lateinit var networkUpdateConsumer: NetworkUpdateConsumer
    private lateinit var permissionUpdateConsumer: PermissionUpdateConsumer
    private lateinit var commandExecuteConsumer: CommandExecuteConsumer
    private lateinit var lifecycleConsumer: LifecycleConsumer
    private lateinit var settings: PaperPluginSettings
    private lateinit var serverApi: OgCloudServerAPIImpl
    private lateinit var apiClient: ApiClient

    val configuredMaxPlayers: Int
        get() = settings.configuredMaxPlayers

    val serverId: String
        get() = settings.serverId

    val groupName: String
        get() = settings.groupName

    override fun onEnable() {
        settings = PaperPluginSettings.fromEnvironment(server.maxPlayers)

        initializeInfrastructure()
        applyConfiguredMaxPlayers()
        loadNetworkFeatures()
        registerListeners()
        registerPublicApi()
        startConsumers()

        logger.info("OgCloud Paper Plugin enabled for server $serverId (group: $groupName)")
    }

    override fun onDisable() {
        stopConsumer(::lifecycleConsumer.isInitialized) { lifecycleConsumer.stop() }
        stopConsumer(::commandExecuteConsumer.isInitialized) { commandExecuteConsumer.stop() }
        stopConsumer(::networkUpdateConsumer.isInitialized) { networkUpdateConsumer.stop() }
        stopConsumer(::permissionUpdateConsumer.isInitialized) { permissionUpdateConsumer.stop() }
        stopConsumer(::heartbeatTask.isInitialized) { heartbeatTask.stop() }
        stopConsumer(::kafkaManager.isInitialized) { kafkaManager.close() }
        stopConsumer(::redisManager.isInitialized) { redisManager.close() }

        OgCloudServerAPI.clear()

        logger.info("OgCloud Paper Plugin disabled")
    }

    private fun initializeInfrastructure() {
        kafkaManager = KafkaManager(settings.kafkaBrokers, serverId, logger).also(KafkaManager::start)
        redisManager = RedisManager(settings.redisHost, settings.redisPort, logger)
        permissionManager = PermissionManager()
        networkFeatureState = NetworkFeatureState()
        tablistTeamManager = TablistTeamManager(permissionManager, networkFeatureState)
        gameStateManager = GameStateManager(serverId, groupName, kafkaManager, logger)
        apiClient = ApiClient(settings.apiUrl, settings.apiEmail, settings.apiPassword, logger)
        heartbeatTask = HeartbeatTask(this, kafkaManager).also(HeartbeatTask::start)
    }

    private fun applyConfiguredMaxPlayers() {
        server.maxPlayers = configuredMaxPlayers
        logger.info("Applied max players for server $serverId: $configuredMaxPlayers")
    }

    private fun registerListeners() {
        server.pluginManager.registerEvents(
            PlayerJoinListener(
                this,
                permissionManager,
                tablistTeamManager,
                networkFeatureState,
                redisManager,
                logger
            ),
            this
        )
        server.pluginManager.registerEvents(
            ChatListener(permissionManager, networkFeatureState),
            this
        )
    }

    private fun registerPublicApi() {
        serverApi = OgCloudServerAPIImpl(
            serverId = serverId,
            groupName = groupName,
            groupType = settings.groupType,
            redisManager = redisManager,
            permissionManager = permissionManager,
            gameStateManager = gameStateManager,
            apiClient = apiClient,
            logger = logger
        )

        server.servicesManager.register(OgCloudServerAPI::class.java, serverApi, this, ServicePriority.Normal)

        OgCloudServerAPI.set(serverApi)
    }

    private fun startConsumers() {
        permissionUpdateConsumer = PermissionUpdateConsumer(
            plugin = this,
            kafkaManager = kafkaManager,
            permissionManager = permissionManager,
            tablistTeamManager = tablistTeamManager,
            networkFeatureState = networkFeatureState,
            logger = logger,
            serverId = serverId
        ).also(PermissionUpdateConsumer::start)

        networkUpdateConsumer = NetworkUpdateConsumer(
            kafkaManager = kafkaManager,
            networkFeatureState = networkFeatureState,
            logger = logger,
            onFeaturesChanged = { permissionSystemEnabled, tablistEnabled ->
                server.scheduler.runTask(
                    this,
                    Runnable { applyNetworkFeatures(permissionSystemEnabled, tablistEnabled) }
                )
            },
            serverId = serverId
        ).also(NetworkUpdateConsumer::start)

        commandExecuteConsumer = CommandExecuteConsumer(
            plugin = this,
            kafkaManager = kafkaManager,
            serverId = serverId,
            groupName = groupName,
            logger = logger
        ).also(CommandExecuteConsumer::start)

        lifecycleConsumer = LifecycleConsumer(
            kafkaManager = kafkaManager,
            serverApi = serverApi,
            logger = logger,
            serverId = serverId
        ).also(LifecycleConsumer::start)
    }

    private fun loadNetworkFeatures() {
        runCatching { apiClient.getNetworkSettingsSync() }
            .onSuccess { settingsResponse ->
                applyNetworkFeatures(
                    settingsResponse.general.permissionSystemEnabled,
                    settingsResponse.general.tablistEnabled
                )
            }
            .onFailure {
                logger.warning(
                    "Failed to load network feature settings, defaulting to enabled: ${it.message}"
                )
                applyNetworkFeatures(permissionSystemEnabled = true, tablistEnabled = true)
            }
    }

    private fun applyNetworkFeatures(permissionSystemEnabled: Boolean, tablistEnabled: Boolean) {
        networkFeatureState.update(permissionSystemEnabled, tablistEnabled)

        if (!permissionSystemEnabled) {
            permissionManager.clear()
        }

        val onlinePlayers = Bukkit.getOnlinePlayers().toList()

        onlinePlayers.forEach { player ->
            if (permissionSystemEnabled) {
                val session = redisManager.getPlayerData(player.uniqueId.toString())
                if (session != null) {
                    permissionManager.cachePlayer(player.uniqueId, session)
                } else {
                    permissionManager.cachePlayerDefault(player.uniqueId)
                }
                PermissionInjector.inject(player, permissionManager, logger)
            } else {
                permissionManager.removePlayer(player.uniqueId)
                PermissionInjector.uninject(player, logger)
            }
        }

        if (tablistEnabled) {
            onlinePlayers.forEach { player ->
                tablistTeamManager.setTablistForMe(player)
                tablistTeamManager.setTablistForOthers(player)
            }
        } else {
            tablistTeamManager.clearAll()
        }
    }

    private inline fun stopConsumer(isInitialized: Boolean, stopAction: () -> Unit) {
        if (isInitialized) {
            stopAction()
        }
    }
}
