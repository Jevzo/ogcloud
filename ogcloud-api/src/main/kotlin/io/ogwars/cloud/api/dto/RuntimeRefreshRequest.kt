package io.ogwars.cloud.api.dto

import io.ogwars.cloud.common.model.RuntimeBundleScope

data class RuntimeRefreshRequest(
    val scope: RuntimeBundleScope,
)
