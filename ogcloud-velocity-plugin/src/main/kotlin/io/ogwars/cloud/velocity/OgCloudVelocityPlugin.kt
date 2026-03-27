package io.ogwars.cloud.velocity

import io.ogwars.cloud.proxy.api.OgCloudProxyAPI
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.api.OgCloudProxyAPIImpl
import io.ogwars.cloud.velocity.channel.LiveChannelConsumer
import io.ogwars.cloud.velocity.channel.LiveChannelManager
import io.ogwars.cloud.velocity.command.OgCloudCommand
import io.ogwars.cloud.velocity.config.VelocityPluginSettings
import io.ogwars.cloud.velocity.heartbeat.ProxyHeartbeatTask
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.kafka.KafkaSendDispatcher
import io.ogwars.cloud.velocity.listener.*
import io.ogwars.cloud.velocity.mongo.MongoManager
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.notification.AdminNotificationManager
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.permission.PermissionCheckListener
import io.ogwars.cloud.velocity.permission.PermissionExpiryTask
import io.ogwars.cloud.velocity.redis.RedisManager
import io.ogwars.cloud.velocity.server.ServerRegistry
import io.ogwars.cloud.velocity.tablist.TablistManager
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Plugin(
    id = "ogcloud",
    name = "OgCloud",
    version = "0.1.0",
    description = "OgCloud Velocity Plugin - Dynamic server registration",
    authors = ["OgCloud"],
)
class OgCloudVelocityPlugin
    @Inject
    constructor(
        private val server: ProxyServer,
        private val logger: Logger,
    ) {
        private lateinit var kafkaManager: KafkaManager
        private lateinit var kafkaSendDispatcher: KafkaSendDispatcher
        private lateinit var kafkaPublishExecutor: ExecutorService
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
        private lateinit var liveChannelConsumer: LiveChannelConsumer
        private lateinit var proxyHeartbeatTask: ProxyHeartbeatTask
        private lateinit var permissionExpiryTask: PermissionExpiryTask
        private lateinit var tablistManager: TablistManager
        private lateinit var settings: VelocityPluginSettings
        private lateinit var proxyApi: OgCloudProxyAPIImpl
        private lateinit var liveChannelManager: LiveChannelManager

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
                settings.proxyMaxPlayers,
            )
        }

        @Subscribe
        fun onProxyShutdown(event: ProxyShutdownEvent) {
            stopIfInitialized(::proxyHeartbeatTask.isInitialized) { proxyHeartbeatTask.stop() }
            stopIfInitialized(::permissionExpiryTask.isInitialized) { permissionExpiryTask.stop() }
            stopIfInitialized(::tablistManager.isInitialized) { tablistManager.stop() }
            stopIfInitialized(::liveChannelConsumer.isInitialized) { liveChannelConsumer.stop() }
            stopIfInitialized(::webAccountLinkOtpConsumer.isInitialized) { webAccountLinkOtpConsumer.stop() }
            stopIfInitialized(::commandExecuteConsumer.isInitialized) { commandExecuteConsumer.stop() }
            stopIfInitialized(::networkUpdateConsumer.isInitialized) { networkUpdateConsumer.stop() }
            stopIfInitialized(::permissionUpdateConsumer.isInitialized) { permissionUpdateConsumer.stop() }
            stopIfInitialized(::groupUpdateConsumer.isInitialized) { groupUpdateConsumer.stop() }
            stopIfInitialized(::playerTransferConsumer.isInitialized) { playerTransferConsumer.stop() }
            stopIfInitialized(::lifecycleConsumer.isInitialized) { lifecycleConsumer.stop() }
            stopIfInitialized(::kafkaPublishExecutor.isInitialized) {
                shutdownExecutor(
                    kafkaPublishExecutor,
                    "velocity-kafka-handoff",
                )
            }
            stopIfInitialized(::kafkaSendDispatcher.isInitialized) { kafkaSendDispatcher.stop() }
            stopIfInitialized(::kafkaManager.isInitialized) { kafkaManager.close() }
            stopIfInitialized(::redisManager.isInitialized) { redisManager.close() }
            stopIfInitialized(::mongoManager.isInitialized) { mongoManager.close() }

            OgCloudProxyAPI.clear()

            logger.info("OgCloud Velocity Plugin shutdown")
        }

        private fun initializeInfrastructure() {
            kafkaManager = KafkaManager(settings.kafkaBrokers, "velocity-${settings.proxyId}").also(KafkaManager::start)
            kafkaSendDispatcher =
                KafkaSendDispatcher(
                    kafkaManager = kafkaManager,
                    logger = logger,
                    workerThreadName = "ogcloud-velocity-kafka-dispatcher",
                ).also(KafkaSendDispatcher::start)
            kafkaPublishExecutor =
                Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "ogcloud-velocity-kafka-handoff").apply { isDaemon = true }
                }
            redisManager = RedisManager(settings.redisHost, settings.redisPort, logger)
            mongoManager = MongoManager(settings.mongoUri, settings.mongoDatabase)
            serverRegistry = ServerRegistry(server, logger)
            permissionCache = PermissionCache()
            adminNotificationManager = AdminNotificationManager(server, permissionCache)
            apiClient = ApiClient(settings.apiUrl, settings.apiEmail, settings.apiPassword, logger)
            liveChannelManager = LiveChannelManager(settings.proxyId, kafkaSendDispatcher, logger)
        }

        private fun loadPersistentState() {
            val permissionGroups = mongoManager.findAllPermissionGroups()
            permissionGroups.firstOrNull { it.default }?.let(permissionCache::setDefaultGroup)

            logger.info("Loaded {} permission groups from MongoDB", permissionGroups.size)

            val networkSettings = mongoManager.findNetworkSettings()
            networkState =
                NetworkState(
                    maintenance = networkSettings.maintenance,
                    maintenanceKickMessage = networkSettings.maintenanceKickMessage,
                    maxPlayers = networkSettings.maxPlayers,
                    defaultGroup = networkSettings.defaultGroup,
                    permissionSystemEnabled = networkSettings.general.permissionSystemEnabled,
                    tablistEnabled = networkSettings.general.tablistEnabled,
                    proxyRoutingStrategy = networkSettings.general.proxyRoutingStrategy,
                )

            runCatching { apiClient.listGroups().join() }
                .onSuccess { groups ->
                    groups.forEach { group ->
                        runCatching { GroupType.valueOf(group.type) }.getOrNull()?.let {
                            serverRegistry.setGroupType(group.id, it)
                        }
                        serverRegistry.setGroupMaintenance(group.id, group.maintenance)
                    }
                }.onFailure { exception ->
                    logger.warn("Failed to bootstrap group metadata from API", exception)
                }

            redisManager.loadRunningServers(serverRegistry)

            tablistManager =
                TablistManager(
                    proxyServer = server,
                    networkState = networkState,
                    permissionCache = permissionCache,
                    serverRegistry = serverRegistry,
                    proxyDisplayName = settings.proxyDisplayName,
                    logger = logger,
                ).apply {
                    headerTemplate = networkSettings.tablist.header
                    footerTemplate = networkSettings.tablist.footer
                    setEnabled(networkSettings.general.tablistEnabled)
                }
        }

        private fun registerPublicApi() {
            proxyApi = OgCloudProxyAPIImpl(permissionCache, apiClient, redisManager, liveChannelManager, logger)
            OgCloudProxyAPI.set(proxyApi)
        }

        private fun startConsumers() {
            lifecycleConsumer =
                LifecycleConsumer(
                    kafkaManager = kafkaManager,
                    serverRegistry = serverRegistry,
                    adminNotificationManager = adminNotificationManager,
                    proxyApi = proxyApi,
                    logger = logger,
                    consumerRecoverySettings = settings.kafkaConsumerRecoverySettings,
                    proxyId = settings.proxyId,
                ).also(LifecycleConsumer::start)

            playerTransferConsumer =
                PlayerTransferConsumer(
                    kafkaManager = kafkaManager,
                    serverRegistry = serverRegistry,
                    permissionCache = permissionCache,
                    networkState = networkState,
                    proxy = server,
                    logger = logger,
                    consumerRecoverySettings = settings.kafkaConsumerRecoverySettings,
                    proxyId = settings.proxyId,
                ).also(PlayerTransferConsumer::start)

            groupUpdateConsumer =
                GroupUpdateConsumer(
                    kafkaManager = kafkaManager,
                    serverRegistry = serverRegistry,
                    adminNotificationManager = adminNotificationManager,
                    permissionCache = permissionCache,
                    networkState = networkState,
                    proxyServer = server,
                    proxyGroup = settings.proxyGroup,
                    logger = logger,
                    consumerRecoverySettings = settings.kafkaConsumerRecoverySettings,
                    proxyId = settings.proxyId,
                ).also(GroupUpdateConsumer::start)

            permissionUpdateConsumer =
                PermissionUpdateConsumer(
                    kafkaManager = kafkaManager,
                    permissionCache = permissionCache,
                    networkState = networkState,
                    proxyServer = server,
                    logger = logger,
                    consumerRecoverySettings = settings.kafkaConsumerRecoverySettings,
                    proxyId = settings.proxyId,
                ).also(PermissionUpdateConsumer::start)

            networkUpdateConsumer =
                NetworkUpdateConsumer(
                    kafkaManager = kafkaManager,
                    networkState = networkState,
                    permissionCache = permissionCache,
                    redisManager = redisManager,
                    proxyServer = server,
                    adminNotificationManager = adminNotificationManager,
                    tablistManager = tablistManager,
                    logger = logger,
                    consumerRecoverySettings = settings.kafkaConsumerRecoverySettings,
                    proxyId = settings.proxyId,
                ).also(NetworkUpdateConsumer::start)

            commandExecuteConsumer =
                CommandExecuteConsumer(
                    kafkaManager = kafkaManager,
                    proxyServer = server,
                    logger = logger,
                    proxyId = settings.proxyId,
                    groupName = settings.proxyGroup,
                    consumerRecoverySettings = settings.kafkaConsumerRecoverySettings,
                ).also(CommandExecuteConsumer::start)

            webAccountLinkOtpConsumer =
                WebAccountLinkOtpConsumer(
                    kafkaManager = kafkaManager,
                    proxyServer = server,
                    logger = logger,
                    consumerRecoverySettings = settings.kafkaConsumerRecoverySettings,
                    proxyId = settings.proxyId,
                ).also(WebAccountLinkOtpConsumer::start)

            liveChannelConsumer =
                LiveChannelConsumer(
                    kafkaManager = kafkaManager,
                    liveChannelManager = liveChannelManager,
                    logger = logger,
                    consumerRecoverySettings = settings.kafkaConsumerRecoverySettings,
                    proxyId = settings.proxyId,
                ).also(LiveChannelConsumer::start)
        }

        private fun registerListeners() {
            server.eventManager.register(
                this,
                PlayerConnectionListener(
                    kafkaSendDispatcher = kafkaSendDispatcher,
                    publishExecutor = kafkaPublishExecutor,
                    redisManager = redisManager,
                    permissionCache = permissionCache,
                    networkState = networkState,
                    serverRegistry = serverRegistry,
                    proxyServer = server,
                    proxyGroup = settings.proxyGroup,
                    proxyMaxPlayers = settings.proxyMaxPlayers,
                    proxyId = settings.proxyId,
                    logger = logger,
                ),
            )
            server.eventManager.register(this, PermissionCheckListener(permissionCache, networkState))
            server.eventManager.register(
                this,
                InitialServerHandler(serverRegistry, permissionCache, networkState, logger),
            )
            server.eventManager.register(
                this,
                ConnectionFailureHandler(serverRegistry, permissionCache, networkState, logger),
            )
        }

        private fun startBackgroundTasks() {
            tablistManager.start()
            permissionExpiryTask =
                PermissionExpiryTask(
                    permissionCache,
                    kafkaSendDispatcher,
                    networkState,
                    logger,
                ).also(PermissionExpiryTask::start)
            proxyHeartbeatTask =
                ProxyHeartbeatTask(
                    proxyServer = server,
                    kafkaSendDispatcher = kafkaSendDispatcher,
                    proxyId = settings.proxyId,
                    maxPlayers = settings.proxyMaxPlayers,
                    podIp = settings.proxyPodIp,
                    port = settings.proxyPort,
                    logger = logger,
                ).also(ProxyHeartbeatTask::start)
        }

        private fun registerCommands() {
            OgCloudCommand.register(server, apiClient, serverRegistry)
        }

        private inline fun stopIfInitialized(
            isInitialized: Boolean,
            action: () -> Unit,
        ) {
            if (isInitialized) {
                action()
            }
        }

        private fun shutdownExecutor(
            executor: ExecutorService,
            name: String,
        ) {
            executor.shutdown()
            try {
                if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate in time: {}", name)
                    executor.shutdownNow()
                }
            } catch (_: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }

        companion object {
            private const val EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5L
        }
    }
