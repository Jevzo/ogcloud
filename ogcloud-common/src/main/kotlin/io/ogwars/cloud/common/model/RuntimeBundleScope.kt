package io.ogwars.cloud.common.model

enum class RuntimeBundleScope(
    val minioPrefix: String,
) {
    VELOCITY("runtime/velocity"),
    PAPER_1_21_11("runtime/paper-1.21.11"),
    PAPER_1_8_8("runtime/paper-1.8.8"),
}
