package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.kafka.RuntimeRefreshProducer
import io.ogwars.cloud.common.model.RuntimeBundleScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RuntimeService(
    private val runtimeRefreshProducer: RuntimeRefreshProducer,
    private val auditLogService: AuditLogService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestRefresh(
        scope: RuntimeBundleScope,
        requestedBy: String? = null,
    ) {
        runtimeRefreshProducer.requestRefresh(scope, requestedBy)

        auditLogService.logApiAction(
            action = "RUNTIME_REFRESH_REQUESTED",
            targetType = "RUNTIME_SCOPE",
            targetId = scope.name,
            summary = "Requested runtime refresh for scope ${scope.name}",
        )

        log.info("Runtime refresh requested: scope={}, requestedBy={}", scope, requestedBy)
    }
}
