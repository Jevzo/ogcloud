package io.ogwars.cloud.common.event

import io.ogwars.cloud.common.model.RuntimeBundleScope

data class RuntimeRefreshRequestedEvent(
    val scope: RuntimeBundleScope,
    val requestedBy: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
