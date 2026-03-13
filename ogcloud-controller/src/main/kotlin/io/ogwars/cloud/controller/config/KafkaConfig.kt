package io.ogwars.cloud.controller.config

import io.ogwars.cloud.common.kafka.KafkaTopics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {
    @Bean
    fun serverLifecycleTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_LIFECYCLE, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun serverLifecycleRetryTopic(): NewTopic = buildRetryTopic(KafkaTopics.SERVER_LIFECYCLE, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun serverLifecycleDltTopic(): NewTopic = buildDltTopic(KafkaTopics.SERVER_LIFECYCLE, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun serverRequestTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_REQUEST)

    @Bean
    fun serverRequestDltTopic(): NewTopic = buildDltTopic(KafkaTopics.SERVER_REQUEST)

    @Bean
    fun serverStopTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_STOP)

    @Bean
    fun serverStopDltTopic(): NewTopic = buildDltTopic(KafkaTopics.SERVER_STOP)

    @Bean
    fun serverHeartbeatTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_HEARTBEAT, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun serverHeartbeatDltTopic(): NewTopic = buildDltTopic(KafkaTopics.SERVER_HEARTBEAT, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun proxyHeartbeatTopic(): NewTopic = buildTopic(KafkaTopics.PROXY_HEARTBEAT)

    @Bean
    fun proxyHeartbeatDltTopic(): NewTopic = buildDltTopic(KafkaTopics.PROXY_HEARTBEAT)

    @Bean
    fun networkUpdateTopic(): NewTopic = buildTopic(KafkaTopics.NETWORK_UPDATE, SINGLE_TOPIC_PARTITION)

    @Bean
    fun networkUpdateRetryTopic(): NewTopic = buildRetryTopic(KafkaTopics.NETWORK_UPDATE, SINGLE_TOPIC_PARTITION)

    @Bean
    fun networkUpdateDltTopic(): NewTopic = buildDltTopic(KafkaTopics.NETWORK_UPDATE, SINGLE_TOPIC_PARTITION)

    @Bean
    fun serverGamestateTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_GAMESTATE, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun serverGamestateDltTopic(): NewTopic = buildDltTopic(KafkaTopics.SERVER_GAMESTATE, BUSY_TOPIC_PARTITIONS)

    @Bean
    fun playerTransferTopic(): NewTopic = buildTopic(KafkaTopics.PLAYER_TRANSFER)

    @Bean
    fun playerTransferRetryTopic(): NewTopic = buildRetryTopic(KafkaTopics.PLAYER_TRANSFER)

    @Bean
    fun playerTransferDltTopic(): NewTopic = buildDltTopic(KafkaTopics.PLAYER_TRANSFER)

    @Bean
    fun groupUpdateTopic(): NewTopic = buildTopic(KafkaTopics.GROUP_UPDATE)

    @Bean
    fun groupUpdateRetryTopic(): NewTopic = buildRetryTopic(KafkaTopics.GROUP_UPDATE)

    @Bean
    fun groupUpdateDltTopic(): NewTopic = buildDltTopic(KafkaTopics.GROUP_UPDATE)

    @Bean
    fun serverTemplatePushTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_TEMPLATE_PUSH)

    @Bean
    fun serverTemplatePushDltTopic(): NewTopic = buildDltTopic(KafkaTopics.SERVER_TEMPLATE_PUSH)

    @Bean
    fun playerConnectTopic(): NewTopic = buildTopic(KafkaTopics.PLAYER_CONNECT)

    @Bean
    fun playerConnectDltTopic(): NewTopic = buildDltTopic(KafkaTopics.PLAYER_CONNECT)

    @Bean
    fun playerDisconnectTopic(): NewTopic = buildTopic(KafkaTopics.PLAYER_DISCONNECT)

    @Bean
    fun playerDisconnectDltTopic(): NewTopic = buildDltTopic(KafkaTopics.PLAYER_DISCONNECT)

    @Bean
    fun playerSwitchTopic(): NewTopic = buildTopic(KafkaTopics.PLAYER_SWITCH)

    @Bean
    fun playerSwitchDltTopic(): NewTopic = buildDltTopic(KafkaTopics.PLAYER_SWITCH)

    @Bean
    fun permissionUpdateTopic(): NewTopic = buildTopic(KafkaTopics.PERMISSION_UPDATE)

    @Bean
    fun permissionUpdateRetryTopic(): NewTopic = buildRetryTopic(KafkaTopics.PERMISSION_UPDATE)

    @Bean
    fun permissionUpdateDltTopic(): NewTopic = buildDltTopic(KafkaTopics.PERMISSION_UPDATE)

    @Bean
    fun permissionGroupUpdatedTopic(): NewTopic = buildTopic(KafkaTopics.PERMISSION_GROUP_UPDATED)

    @Bean
    fun permissionGroupUpdatedDltTopic(): NewTopic = buildDltTopic(KafkaTopics.PERMISSION_GROUP_UPDATED)

    @Bean
    fun defaultPermissionGroupChangedTopic(): NewTopic = buildTopic(KafkaTopics.DEFAULT_PERMISSION_GROUP_CHANGED)

    @Bean
    fun defaultPermissionGroupChangedDltTopic(): NewTopic = buildDltTopic(KafkaTopics.DEFAULT_PERMISSION_GROUP_CHANGED)

    @Bean
    fun permissionExpiryTopic(): NewTopic = buildTopic(KafkaTopics.PERMISSION_EXPIRY)

    @Bean
    fun permissionExpiryDltTopic(): NewTopic = buildDltTopic(KafkaTopics.PERMISSION_EXPIRY)

    @Bean
    fun serverKillTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_KILL)

    @Bean
    fun serverKillDltTopic(): NewTopic = buildDltTopic(KafkaTopics.SERVER_KILL)

    @Bean
    fun runtimeRefreshRequestedTopic(): NewTopic =
        buildTopic(KafkaTopics.RUNTIME_REFRESH_REQUESTED, SINGLE_TOPIC_PARTITION)

    @Bean
    fun runtimeRefreshRequestedRetryTopic(): NewTopic =
        buildRetryTopic(KafkaTopics.RUNTIME_REFRESH_REQUESTED, SINGLE_TOPIC_PARTITION)

    @Bean
    fun runtimeRefreshRequestedDltTopic(): NewTopic =
        buildDltTopic(KafkaTopics.RUNTIME_REFRESH_REQUESTED, SINGLE_TOPIC_PARTITION)

    private fun buildTopic(
        name: String,
        partitions: Int = LIGHT_TOPIC_PARTITIONS,
    ): NewTopic =
        TopicBuilder
            .name(name)
            .partitions(partitions)
            .replicas(DEFAULT_REPLICAS)
            .build()

    private fun buildDltTopic(
        sourceTopicName: String,
        partitions: Int = LIGHT_TOPIC_PARTITIONS,
    ): NewTopic = buildTopic(KafkaTopics.dltTopic(sourceTopicName), partitions)

    private fun buildRetryTopic(
        sourceTopicName: String,
        partitions: Int = LIGHT_TOPIC_PARTITIONS,
    ): NewTopic = buildTopic(KafkaTopics.retryTopic(sourceTopicName), partitions)

    companion object {
        private const val DEFAULT_REPLICAS = 1
        private const val LIGHT_TOPIC_PARTITIONS = 3
        private const val BUSY_TOPIC_PARTITIONS = 6
        private const val SINGLE_TOPIC_PARTITION = 1
    }
}
