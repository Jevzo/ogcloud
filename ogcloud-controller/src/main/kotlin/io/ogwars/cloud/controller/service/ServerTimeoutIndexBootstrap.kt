package io.ogwars.cloud.controller.service

import io.ogwars.cloud.controller.redis.ServerRedisRepository
import io.ogwars.cloud.controller.repository.GroupRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class ServerTimeoutIndexBootstrap(
    private val serverRedisRepository: ServerRedisRepository,
    private val groupRepository: GroupRepository,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val summary = serverRedisRepository.rebuildTimeoutIndexes(groupRepository.findAll().map { it.id })

        log.info(
            "Rebuilt Redis server timeout indexes: liveServers={}, starting={}, heartbeat={}, draining={}, groupIndexesReset={}",
            summary.liveServerCount,
            summary.startingIndexCount,
            summary.heartbeatIndexCount,
            summary.drainingIndexCount,
            summary.groupIndexCount,
        )
    }
}
