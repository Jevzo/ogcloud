plugins {
    id("ogcloud.kotlin-library")
    id("com.gradleup.shadow")
}

// Third-party plugins on the same server ship their own copies of these libraries.
private val shadedPackages =
    listOf("io.grpc", "io.netty", "com.google.protobuf", "com.google.common", "kotlin", "kotlinx")
private val shadedPrefix = "io.ogwars.cloud.shaded"

tasks.shadowJar {
    mergeServiceFiles()
    shadedPackages.forEach { relocate(it, "$shadedPrefix.$it") }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
