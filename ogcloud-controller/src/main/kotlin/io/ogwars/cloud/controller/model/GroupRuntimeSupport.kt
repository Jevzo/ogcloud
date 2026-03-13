package io.ogwars.cloud.controller.model

import io.ogwars.cloud.common.model.BackendRuntimeProfile
import io.ogwars.cloud.common.model.GroupType

fun GroupDocument.resolvedRuntimeProfile(): BackendRuntimeProfile? =
    when (type) {
        GroupType.PROXY -> null
        else -> runtimeProfile ?: BackendRuntimeProfile.MODERN_1_21_11
    }
