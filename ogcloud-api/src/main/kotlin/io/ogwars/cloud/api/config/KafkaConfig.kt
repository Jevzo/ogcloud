package io.ogwars.cloud.api.config

import io.ogwars.cloud.common.kafka.KafkaTopics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {
    @Bean
    fun serverRequestTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_REQUEST)

    @Bean
    fun serverStopTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_STOP)

    @Bean
    fun networkUpdateTopic(): NewTopic = buildTopic(KafkaTopics.NETWORK_UPDATE, SINGLE_TOPIC_PARTITION)

    @Bean
    fun groupUpdateTopic(): NewTopic = buildTopic(KafkaTopics.GROUP_UPDATE)

    @Bean
    fun serverTemplatePushTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_TEMPLATE_PUSH)

    @Bean
    fun permissionUpdateTopic(): NewTopic = buildTopic(KafkaTopics.PERMISSION_UPDATE)

    @Bean
    fun permissionGroupUpdatedTopic(): NewTopic = buildTopic(KafkaTopics.PERMISSION_GROUP_UPDATED)

    @Bean
    fun defaultPermissionGroupChangedTopic(): NewTopic = buildTopic(KafkaTopics.DEFAULT_PERMISSION_GROUP_CHANGED)

    @Bean
    fun playerTransferTopic(): NewTopic = buildTopic(KafkaTopics.PLAYER_TRANSFER)

    @Bean
    fun serverKillTopic(): NewTopic = buildTopic(KafkaTopics.SERVER_KILL)

    @Bean
    fun commandExecuteTopic(): NewTopic = buildTopic(KafkaTopics.COMMAND_EXECUTE)

    @Bean
    fun commandExecuteRetryTopic(): NewTopic = buildRetryTopic(KafkaTopics.COMMAND_EXECUTE)

    @Bean
    fun commandExecuteDltTopic(): NewTopic = buildDltTopic(KafkaTopics.COMMAND_EXECUTE)

    @Bean
    fun webAccountLinkOtpTopic(): NewTopic = buildTopic(KafkaTopics.WEB_ACCOUNT_LINK_OTP)

    @Bean
    fun webAccountLinkOtpRetryTopic(): NewTopic = buildRetryTopic(KafkaTopics.WEB_ACCOUNT_LINK_OTP)

    @Bean
    fun webAccountLinkOtpDltTopic(): NewTopic = buildDltTopic(KafkaTopics.WEB_ACCOUNT_LINK_OTP)

    @Bean
    fun runtimeRefreshRequestedTopic(): NewTopic =
        buildTopic(KafkaTopics.RUNTIME_REFRESH_REQUESTED, SINGLE_TOPIC_PARTITION)

    private fun buildTopic(
        name: String,
        partitions: Int = DEFAULT_TOPIC_PARTITIONS,
    ): NewTopic =
        TopicBuilder
            .name(name)
            .partitions(partitions)
            .replicas(DEFAULT_TOPIC_REPLICAS)
            .build()

    private fun buildRetryTopic(
        sourceTopicName: String,
        partitions: Int = DEFAULT_TOPIC_PARTITIONS,
    ): NewTopic = buildTopic(KafkaTopics.retryTopic(sourceTopicName), partitions)

    private fun buildDltTopic(
        sourceTopicName: String,
        partitions: Int = DEFAULT_TOPIC_PARTITIONS,
    ): NewTopic = buildTopic(KafkaTopics.dltTopic(sourceTopicName), partitions)

    companion object {
        private const val DEFAULT_TOPIC_PARTITIONS = 3
        private const val SINGLE_TOPIC_PARTITION = 1
        private const val DEFAULT_TOPIC_REPLICAS = 1
    }
}
