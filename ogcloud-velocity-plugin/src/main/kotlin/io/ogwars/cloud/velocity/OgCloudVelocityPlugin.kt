package io.ogwars.cloud.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.proxy.api.OgCloudProxyAPI
import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.api.OgCloudProxyAPIImpl
import io.ogwars.cloud.velocity.command.OgCloudCommand
import io.ogwars.cloud.velocity.config.VelocityPluginSettings
import io.ogwars.cloud.velocity.heartbeat.ProxyHeartbeatTask
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.listener.CommandExecuteConsumer
import io.ogwars.cloud.velocity.listener.ConnectionFailureHandler
import io.ogwars.cloud.velocity.listener.GroupUpdateConsumer
import io.ogwars.cloud.velocity.listener.InitialServerHandler
import io.ogwars.cloud.velocity.listener.LifecycleConsumer
import io.ogwars.cloud.velocity.listener.NetworkUpdateConsumer
import io.ogwars.cloud.velocity.listener.PermissionUpdateConsumer
import io.ogwars.cloud.velocity.listener.PlayerConnectionListener
import io.ogwars.cloud.velocity.listener.PlayerTransferConsumer
import io.ogwars.cloud.velocity.listener.WebAccountLinkOtpConsumer
import io.ogwars.cloud.velocity.mongo.MongoManager
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.notification.AdminNotificationManager
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.permission.PermissionCheckListener
import io.ogwars.cloud.velocity.permission.PermissionExpiryTask
import io.ogwars.cloud.velocity.redis.RedisManager
import io.ogwars.cloud.velocity.server.ServerRegistry
import io.ogwars.cloud.velocity.tablist.TablistManager
import org.slf4j.Logger

@Plugin(
    id = "ogcloud",
    name = "OgCloud",
    version = "0.1.0",
    description = "OgCloud Velocity Plugin - Dynamic server registration",
    authors = ["OgCloud"]
)
class OgCloudVelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger
) {

    private lateinit var kafkaManager: KafkaManager
    private lateinit var redisManager: RedisManager
    private lateinit var mongoManager: MongoManager
    private lateinit var serverRegistry: ServerRegistry
    private lateinit var permissionCache: PermissionCache
    private lateinit var networkState: NetworkState
    private lateinit var adminNotificationManager: AdminNotificationManager
    private lateinit var apiClient: ApiClient
    private lateinit var lifecycleConsumer: LifecycleConsumer
    private lateinit var playerTransferConsumer: PlayerTransferConsumer
    private lateinit var groupUpdateConsumer: GroupUpdateConsumer
    private lateinit var permissionUpdateConsumer: PermissionUpdateConsumer
    private lateinit var networkUpdateConsumer: NetworkUpdateConsumer
    private lateinit var commandExecuteConsumer: CommandExecuteConsumer
    private lateinit var webAccountLinkOtpConsumer: WebAccountLinkOtpConsumer
    private lateinit var proxyHeartbeatTask: ProxyHeartbeatTask
    private lateinit var permissionExpiryTask: PermissionExpiryTask
    private lateinit var tablistManager: TablistManager
    private lateinit var settings: VelocityPluginSettings
    private lateinit var proxyApi: OgCloudProxyAPIImpl

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        settings = VelocityPluginSettings.fromEnvironment()

        initializeInfrastructure()
        loadPersistentState()
        registerPublicApi()
        startConsumers()
        registerListeners()
        startBackgroundTasks()
        registerCommands()

        logger.info(
            "OgCloud Velocity Plugin initialized (proxyId={}, proxyGroup={}, defaultGroup={}, maxPlayers={})",
            settings.proxyId,
            settings.proxyGroup,
            settings.defaultGroup,
            settings.proxyMaxPlayers
        )
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        stopIfInitialized(::proxyHeartbeatTask.isInitialized) { proxyHeartbeatTask.stop() }
        stopIfInitialized(::permissionExpiryTask.isInitialized) { permissionExpiryTask.stop() }
        stopIfInitialized(::tablistManager.isInitialized) { tablistManager.stop() }
        stopIfInitialized(::webAccountLinkOtpConsumer.isInitialized) { webAccountLinkOtpConsumer.stop() }
        stopIfInitialized(::commandExecuteConsumer.isInitialized) { commandExecuteConsumer.stop() }
        stopIfInitialized(::networkUpdateConsumer.isInitialized) { networkUpdateConsumer.stop() }
        stopIfInitialized(::permissionUpdateConsumer.isInitialized) { permissionUpdateConsumer.stop() }
        stopIfInitialized(::groupUpdateConsumer.isInitialized) { groupUpdateConsumer.stop() }
        stopIfInitialized(::playerTransferConsumer.isInitialized) { playerTransferConsumer.stop() }
        stopIfInitialized(::lifecycleConsumer.isInitialized) { lifecycleConsumer.stop() }
        stopIfInitialized(::kafkaManager.isInitialized) { kafkaManager.close() }
        stopIfInitialized(::redisManager.isInitialized) { redisManager.close() }
        stopIfInitialized(::mongoManager.isInitialized) { mongoManager.close() }

        OgCloudProxyAPI.clear()

        logger.info("OgCloud Velocity Plugin shutdown")
    }

    private fun initializeInfrastructure() {
        kafkaManager = KafkaManager(settings.kafkaBrokers, "velocity-${settings.proxyId}").also(KafkaManager::start)
        redisManager = RedisManager(settings.redisHost, settings.redisPort, logger)
        mongoManager = MongoManager(settings.mongoUri, settings.mongoDatabase)
        serverRegistry = ServerRegistry(server, logger)
        permissionCache = PermissionCache()
        adminNotificationManager = AdminNotificationManager(server, permissionCache)
        apiClient = ApiClient(settings.apiUrl, settings.apiEmail, settings.apiPassword, logger)
    }

    private fun loadPersistentState() {
        val permissionGroups = mongoManager.findAllPermissionGroups()
        permissionGroups.firstOrNull { it.default }?.let(permissionCache::setDefaultGroup)

        logger.info("Loaded {} permission groups from MongoDB", permissionGroups.size)

        val networkSettings = mongoManager.findNetworkSettings()
        networkState = NetworkState(
            maintenance = networkSettings.maintenance,
            maintenanceKickMessage = networkSettings.maintenanceKickMessage,
            maxPlayers = networkSettings.maxPlayers,
            defaultGroup = networkSettings.defaultGroup,
            permissionSystemEnabled = networkSettings.general.permissionSystemEnabled,
            tablistEnabled = networkSettings.general.tablistEnabled
        )

        redisManager.loadRunningServers(serverRegistry)

        tablistManager = TablistManager(
            proxyServer = server,
            networkState = networkState,
            permissionCache = permissionCache,
            serverRegistry = serverRegistry,
            proxyDisplayName = settings.proxyDisplayName,
            logger = logger
        ).apply {
            headerTemplate = networkSettings.tablist.header
            footerTemplate = networkSettings.tablist.footer
            setEnabled(networkSettings.general.tablistEnabled)
        }
    }

    private fun registerPublicApi() {
        proxyApi = OgCloudProxyAPIImpl(permissionCache, apiClient, redisManager, logger)
        OgCloudProxyAPI.set(proxyApi)
    }

    private fun startConsumers() {
        lifecycleConsumer = LifecycleConsumer(
            kafkaManager = kafkaManager,
            serverRegistry = serverRegistry,
            adminNotificationManager = adminNotificationManager,
            proxyApi = proxyApi,
            logger = logger,
            proxyId = settings.proxyId
        ).also(LifecycleConsumer::start)

        playerTransferConsumer = PlayerTransferConsumer(
            kafkaManager = kafkaManager,
            serverRegistry = serverRegistry,
            permissionCache = permissionCache,
            networkState = networkState,
            proxy = server,
            logger = logger,
            proxyId = settings.proxyId
        ).also(PlayerTransferConsumer::start)

        groupUpdateConsumer = GroupUpdateConsumer(
            kafkaManager = kafkaManager,
            serverRegistry = serverRegistry,
            adminNotificationManager = adminNotificationManager,
            permissionCache = permissionCache,
            networkState = networkState,
            proxyServer = server,
            proxyGroup = settings.proxyGroup,
            logger = logger,
            proxyId = settings.proxyId
        ).also(GroupUpdateConsumer::start)

        permissionUpdateConsumer = PermissionUpdateConsumer(
            kafkaManager = kafkaManager,
            permissionCache = permissionCache,
            networkState = networkState,
            logger = logger,
            proxyId = settings.proxyId
        ).also(PermissionUpdateConsumer::start)

        networkUpdateConsumer = NetworkUpdateConsumer(
            kafkaManager = kafkaManager,
            networkState = networkState,
            permissionCache = permissionCache,
            redisManager = redisManager,
            proxyServer = server,
            adminNotificationManager = adminNotificationManager,
            tablistManager = tablistManager,
            logger = logger,
            proxyId = settings.proxyId
        ).also(NetworkUpdateConsumer::start)

        commandExecuteConsumer = CommandExecuteConsumer(
            kafkaManager = kafkaManager,
            proxyServer = server,
            logger = logger,
            proxyId = settings.proxyId,
            groupName = settings.proxyGroup
        ).also(CommandExecuteConsumer::start)

        webAccountLinkOtpConsumer = WebAccountLinkOtpConsumer(
            kafkaManager = kafkaManager,
            proxyServer = server,
            logger = logger,
            proxyId = settings.proxyId
        ).also(WebAccountLinkOtpConsumer::start)
    }

    private fun registerListeners() {
        server.eventManager.register(
            this,
            PlayerConnectionListener(
                kafkaManager = kafkaManager,
                redisManager = redisManager,
                permissionCache = permissionCache,
                networkState = networkState,
                serverRegistry = serverRegistry,
                proxyServer = server,
                proxyGroup = settings.proxyGroup,
                proxyMaxPlayers = settings.proxyMaxPlayers,
                proxyId = settings.proxyId,
                logger = logger
            )
        )
        server.eventManager.register(this, PermissionCheckListener(permissionCache, networkState))
        server.eventManager.register(this, InitialServerHandler(serverRegistry, permissionCache, networkState, logger))
        server.eventManager.register(this, ConnectionFailureHandler(serverRegistry, permissionCache, networkState, logger))
    }

    private fun startBackgroundTasks() {
        tablistManager.start()
        permissionExpiryTask = PermissionExpiryTask(
            permissionCache,
            kafkaManager,
            networkState,
            logger
        ).also(PermissionExpiryTask::start)
        proxyHeartbeatTask = ProxyHeartbeatTask(
            proxyServer = server,
            kafkaManager = kafkaManager,
            proxyId = settings.proxyId,
            maxPlayers = settings.proxyMaxPlayers,
            podIp = settings.proxyPodIp,
            port = settings.proxyPort,
            logger = logger
        ).also(ProxyHeartbeatTask::start)
    }

    private fun registerCommands() {
        OgCloudCommand.register(server, apiClient, serverRegistry, logger)
    }

    private inline fun stopIfInitialized(isInitialized: Boolean, action: () -> Unit) {
        if (isInitialized) {
            action()
        }
    }
}
