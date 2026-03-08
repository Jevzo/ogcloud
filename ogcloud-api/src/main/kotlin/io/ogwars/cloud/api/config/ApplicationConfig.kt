package io.ogwars.cloud.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class ApplicationConfig {
    @Bean
    fun groupOperationTaskExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = GROUP_TASK_EXECUTOR_POOL_SIZE
            maxPoolSize = GROUP_TASK_EXECUTOR_POOL_SIZE
            queueCapacity = GROUP_TASK_EXECUTOR_QUEUE_CAPACITY

            setThreadNamePrefix(GROUP_TASK_EXECUTOR_THREAD_PREFIX)

            initialize()
        }

    companion object {
        private const val GROUP_TASK_EXECUTOR_POOL_SIZE = 1
        private const val GROUP_TASK_EXECUTOR_QUEUE_CAPACITY = 32
        private const val GROUP_TASK_EXECUTOR_THREAD_PREFIX = "ogcloud-api-group-"
    }
}
