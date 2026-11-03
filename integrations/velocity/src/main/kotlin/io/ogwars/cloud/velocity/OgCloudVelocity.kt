package io.ogwars.cloud.velocity

import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.node.client.NodeSettings
import io.ogwars.cloud.node.core.runtime.GatewayConnection
import io.ogwars.cloud.node.core.runtime.NodeIdentity
import io.ogwars.cloud.node.core.runtime.ProxyRuntime
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.sdk.OgCloud
import io.ogwars.cloud.sdk.OgCloudInternalApi
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** OgCloud's Velocity plugin: connects the proxy to the gateway (D-0008) and adapts node-core to Velocity. */
class OgCloudVelocity
    @Inject
    constructor(
        private val proxy: ProxyServer,
    ) {
        private var node: Node? = null

        // Velocity picks the event by the parameter's type, so the parameter stays though it is unused.
        @Subscribe
        fun onInitialize(
            @Suppress("UNUSED_PARAMETER") event: ProxyInitializeEvent,
        ) {
            val version =
                proxy.pluginManager
                    .ensurePluginContainer(this)
                    .description.version
                    .orElse(UNKNOWN_VERSION)
            node = Node(version).also { it.start() }
        }

        // As above, the parameter only names the event.
        @Subscribe
        fun onShutdown(
            @Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent,
        ) {
            node?.close()
            node = null
        }

        /** Everything that lives while the proxy runs. */
        private inner class Node(
            version: String,
        ) : AutoCloseable {
            private val environment = System.getenv()
            private val identity = NodeIdentity.fromEnvironment(environment)
            private val scheduler: ScheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor { Thread(it, "ogcloud").apply { isDaemon = true } }
            private val connection =
                GatewayConnection(
                    NodeSettings.fromEnvironment(environment, NodeType.NODE_TYPE_PROXY, version),
                    scheduler,
                )
            private val runtime =
                ProxyRuntime(
                    identity,
                    VelocityPlatform(proxy, ViaProtocols.ifInstalled(proxy)),
                    connection.queries,
                    Messages.load(),
                    scheduler,
                    Clock.systemUTC(),
                    connection::session,
                )

            @Volatile
            private var displayName = identity.serverId
            private var tablist: ScheduledTask? = null

            @OptIn(OgCloudInternalApi::class)
            fun start() {
                OgCloud.install(runtime.api)
                proxy.eventManager.register(this@OgCloudVelocity, VelocityListeners(proxy, runtime))
                val meta =
                    proxy.commandManager
                        .metaBuilder(COMMAND)
                        .aliases(ALIAS)
                        .plugin(this@OgCloudVelocity)
                        .build()
                proxy.commandManager.register(meta, OgCloudCommand(runtime.commands))
                val updater = TablistUpdater(proxy, runtime) { displayName }
                tablist =
                    proxy.scheduler
                        .buildTask(this@OgCloudVelocity, Runnable { updater.refresh() })
                        .repeat(TABLIST_SECONDS, TimeUnit.SECONDS)
                        .schedule()
                runtime.start()
                runtime.api.onReady {
                    runtime.queries.server(identity.serverId).thenAccept { info ->
                        info?.let {
                            displayName =
                                it.displayName
                        }
                    }
                }
            }

            @OptIn(OgCloudInternalApi::class)
            override fun close() {
                tablist?.cancel()
                OgCloud.install(null)
                runtime.close()
                connection.close()
                scheduler.shutdown()
            }
        }

        private companion object {
            const val COMMAND = "ogcloud"
            const val ALIAS = "oc"
            const val TABLIST_SECONDS = 2L
            const val UNKNOWN_VERSION = "unknown"
        }
    }
