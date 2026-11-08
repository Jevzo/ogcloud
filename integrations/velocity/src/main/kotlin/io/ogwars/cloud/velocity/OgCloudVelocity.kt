package io.ogwars.cloud.velocity

import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.node.core.runtime.NodePod
import io.ogwars.cloud.node.core.runtime.ProxyRuntime
import io.ogwars.cloud.sdk.OgCloud
import io.ogwars.cloud.sdk.OgCloudInternalApi
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import java.time.Clock
import java.util.concurrent.TimeUnit

/** OgCloud's Velocity plugin: connects the proxy to the gateway (D-0008) and adapts node-core to Velocity. */
class OgCloudVelocity
    @Inject
    constructor(
        private val proxy: ProxyServer,
    ) {
        private val log = System.getLogger(OgCloudVelocity::class.java.name)
        private var node: Node? = null

        // Velocity picks the event by the parameter's type, so the parameter stays though it is unused. If OgCloud
        // cannot start the proxy stops: it would route nobody, and the controller would only see a missing heartbeat.
        @Subscribe
        fun onInitialize(
            @Suppress("UNUSED_PARAMETER") event: ProxyInitializeEvent,
        ) {
            val version =
                proxy.pluginManager
                    .ensurePluginContainer(this)
                    .description.version
                    .orElse(UNKNOWN_VERSION)
            node =
                try {
                    Node(version).also { it.start() }
                } catch (e: Exception) {
                    log.log(System.Logger.Level.ERROR, "OgCloud could not start, so the proxy stops", e)
                    proxy.shutdown()
                    throw e
                }
        }

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
            private val pod = NodePod(NodeType.NODE_TYPE_PROXY, version)
            private val identity = pod.identity
            private val runtime =
                ProxyRuntime(
                    identity,
                    VelocityPlatform(proxy, ViaProtocols.ifInstalled(proxy)),
                    pod.connection.queries,
                    pod.messages,
                    pod.scheduler,
                    pod.connection.callbacks,
                    Clock.systemUTC(),
                    pod.connection::session,
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
                    runtime.queries.server(identity.serverId).whenComplete { info, error ->
                        if (error != null) {
                            log.log(System.Logger.Level.WARNING, "Could not look up this proxy's display name", error)
                        } else if (info != null) {
                            displayName = info.displayName
                        }
                    }
                }
            }

            @OptIn(OgCloudInternalApi::class)
            override fun close() {
                tablist?.cancel()
                OgCloud.install(null)
                runtime.close()
                pod.close()
            }
        }

        private companion object {
            const val COMMAND = "ogcloud"
            const val ALIAS = "oc"
            const val TABLIST_SECONDS = 2L
            const val UNKNOWN_VERSION = "unknown"
        }
    }
