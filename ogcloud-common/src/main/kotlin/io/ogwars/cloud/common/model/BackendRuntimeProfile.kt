package io.ogwars.cloud.common.model

enum class BackendRuntimeProfile(
    val minecraftVersion: String,
    val runtimeScope: RuntimeBundleScope,
) {
    LEGACY_1_8_8(
        minecraftVersion = "1.8.8",
        runtimeScope = RuntimeBundleScope.PAPER_1_8_8,
    ),
    MODERN_1_21_11(
        minecraftVersion = "1.21.11",
        runtimeScope = RuntimeBundleScope.PAPER_1_21_11,
    ),
}
