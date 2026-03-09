package io.ogwars.cloud.controller.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {
    @Bean
    fun serverLifecycleTopic(): NewTopic = buildTopic(SERVER_LIFECYCLE, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun serverRequestTopic(): NewTopic = buildTopic(SERVER_REQUEST)

    @Bean
    fun serverStopTopic(): NewTopic = buildTopic(SERVER_STOP)

    @Bean
    fun serverHeartbeatTopic(): NewTopic = buildTopic(SERVER_HEARTBEAT, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun proxyHeartbeatTopic(): NewTopic = buildTopic(PROXY_HEARTBEAT)

    @Bean
    fun networkUpdateTopic(): NewTopic = buildTopic(NETWORK_UPDATE, SINGLE_TOPIC_PARTITION)

    @Bean
    fun serverGamestateTopic(): NewTopic = buildTopic(SERVER_GAMESTATE, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun playerTransferTopic(): NewTopic = buildTopic(PLAYER_TRANSFER)

    @Bean
    fun groupUpdateTopic(): NewTopic = buildTopic(GROUP_UPDATE)

    @Bean
    fun serverTemplatePushTopic(): NewTopic = buildTopic(SERVER_TEMPLATE_PUSH)

    @Bean
    fun playerConnectTopic(): NewTopic = buildTopic(PLAYER_CONNECT)

    @Bean
    fun playerDisconnectTopic(): NewTopic = buildTopic(PLAYER_DISCONNECT)

    @Bean
    fun playerSwitchTopic(): NewTopic = buildTopic(PLAYER_SWITCH)

    @Bean
    fun permissionUpdateTopic(): NewTopic = buildTopic(PERMISSION_UPDATE)

    @Bean
    fun permissionGroupUpdatedTopic(): NewTopic = buildTopic(PERMISSION_GROUP_UPDATED)

    @Bean
    fun defaultPermissionGroupChangedTopic(): NewTopic = buildTopic(DEFAULT_PERMISSION_GROUP_CHANGED)

    @Bean
    fun permissionExpiryTopic(): NewTopic = buildTopic(PERMISSION_EXPIRY)

    @Bean
    fun serverKillTopic(): NewTopic = buildTopic(SERVER_KILL)

    private fun buildTopic(
        name: String,
        partitions: Int = LIGHT_TOPIC_PARTITIONS,
    ): NewTopic =
        TopicBuilder
            .name(name)
            .partitions(partitions)
            .replicas(DEFAULT_REPLICAS)
            .build()

    companion object {
        const val SERVER_LIFECYCLE = "ogcloud.server.lifecycle"
        const val SERVER_REQUEST = "ogcloud.server.request"
        const val SERVER_STOP = "ogcloud.server.stop"
        const val SERVER_HEARTBEAT = "ogcloud.server.heartbeat"
        const val PROXY_HEARTBEAT = "ogcloud.proxy.heartbeat"
        const val NETWORK_UPDATE = "ogcloud.network.update"
        const val SERVER_GAMESTATE = "ogcloud.server.gamestate"
        const val PLAYER_TRANSFER = "ogcloud.player.transfer"
        const val GROUP_UPDATE = "ogcloud.group.update"
        const val SERVER_TEMPLATE_PUSH = "ogcloud.server.template.push"
        const val PLAYER_CONNECT = "ogcloud.player.connect"
        const val PLAYER_DISCONNECT = "ogcloud.player.disconnect"
        const val PLAYER_SWITCH = "ogcloud.player.switch"
        const val PERMISSION_UPDATE = "ogcloud.permission.update"
        const val PERMISSION_GROUP_UPDATED = "ogcloud.permission.group.updated"
        const val DEFAULT_PERMISSION_GROUP_CHANGED = "ogcloud.permission.group.default.changed"
        const val PERMISSION_EXPIRY = "ogcloud.permission.expiry"
        const val SERVER_KILL = "ogcloud.server.kill"

        private const val DEFAULT_REPLICAS = 1
        private const val LIGHT_TOPIC_PARTITIONS = 3
        private const val BUSY_TOPIC_PARTITIONS = 6
        private const val SINGLE_TOPIC_PARTITION = 1
    }
}
