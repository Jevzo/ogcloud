plugins {
    id("ogcloud.published-library")
    // The public API is dumped to api/sdk.api; a change fails the build until the dump is reviewed (D-0127).
    alias(libs.plugins.binary.compatibility.validator)
}

description = "The OgCloud plugin API for Minecraft servers and proxies"

dependencies {
    // The validator's own ASM cannot read Java 25 classes (D-0066).
    "bcv-rt-jvm-cp"(libs.asm)
    "bcv-rt-jvm-cp"(libs.asm.tree)
}
