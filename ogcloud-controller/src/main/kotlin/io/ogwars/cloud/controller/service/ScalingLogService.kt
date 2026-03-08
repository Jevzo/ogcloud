package io.ogwars.cloud.controller.service

import io.ogwars.cloud.controller.model.ScalingLogDocument
import io.ogwars.cloud.controller.repository.ScalingLogRepository
import org.springframework.stereotype.Service

@Service
class ScalingLogService(
    private val scalingLogRepository: ScalingLogRepository,
) {
    fun logDecision(
        groupId: String,
        action: String,
        reason: String,
        serverId: String? = null,
        details: String? = null,
    ) {
        scalingLogRepository.save(
            ScalingLogDocument(
                groupId = groupId,
                action = action,
                reason = reason,
                serverId = serverId,
                details = details,
            ),
        )
    }
}
